/*
 * Copyright 2013 Michael Krolikowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mkroli.dss

import java.net.InetSocketAddress
import com.github.mkroli.dss.dns.Message
import com.github.mkroli.dss.dns.akka.DnsActor
import com.github.mkroli.dss.dns.akka.DnsPacket
import com.github.mkroli.dss.dns.dsl.DnsMessage
import com.github.mkroli.dss.dns.dsl.ErrorMessage
import com.github.mkroli.dss.dns.section.QuestionSection
import com.github.mkroli.dss.dns.section.ResourceRecord
import com.github.mkroli.dss.dns.section.resource.CNameResource
import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.pattern.pipe
import scala.concurrent.Future

trait DnsFrontendComponent {
  self: AkkaComponent with IndexComponent with ConfigurationComponent with MetricsComponent =>

  lazy val listenPort: Int = config.getInt("server.bind.port")
  lazy val fallbackDnsAddress = new InetSocketAddress(
    config.getString("server.fallback.address"),
    config.getInt("server.fallback.port"))

  lazy val dnsHandlerActor = actorSystem.actorOf(Props(new DnsHandlerActor))
  lazy val dnsActor = actorSystem.actorOf(Props(new DnsActor(listenPort, dnsHandlerActor, timeout)))

  class DnsHandlerActor extends Actor with Instrumented {
    val requestsMetric = metrics.meter("requestsMeter")
    val overallLookupTimer = metrics.timer("overallLookupTimer")

    private object SearchableQuestion {
      def unapply(message: Message) = message.question.find {
        case QuestionSection(_,
          ResourceRecord.typeA |
          ResourceRecord.typeAAAA,
          ResourceRecord.classIN) => true
        case _ => false
      }
    }

    override def receive = {
      case originalRequest: Message =>
        requestsMetric.mark
        val overallLookup = overallLookupTimer.timerContext
        val origin = sender
        dnsActor ? DnsPacket(originalRequest, fallbackDnsAddress) flatMap {
          case ErrorMessage(answer @ SearchableQuestion(question)) =>
            indexActor ? SearchIndex(question.qname.replace('.', ' ')) flatMap {
              case Some(result: String) =>
                val lookup = DnsPacket(
                  DnsMessage.withQuestion(question.copy(qname = result)).build,
                  fallbackDnsAddress)
                dnsActor ? lookup map {
                  case answer: Message =>
                    val baseResult = DnsMessage(answer)
                      .withoutQuestions
                      .withoutAnswers
                    val resultWithQuestions = originalRequest.question.foldLeft(baseResult) { (dnsMessage, question) =>
                      dnsMessage.withQuestion(question)
                    }
                    val resultWithSearchResult = resultWithQuestions.withAnswer(question.qname, CNameResource(result))
                    val resultWithAnswers = answer.answer.foldLeft(resultWithSearchResult) { (dnsMessage, answer) =>
                      dnsMessage.withAnswer(answer.name, answer.rdata, answer.ttl, answer.`class`)
                    }
                    resultWithAnswers.build
                }
              case _ => Future(answer)
            }
          case answer: Message => Future(answer)
        } pipeTo origin onSuccess {
          case _ => overallLookup.stop
        }
    }
  }
}
