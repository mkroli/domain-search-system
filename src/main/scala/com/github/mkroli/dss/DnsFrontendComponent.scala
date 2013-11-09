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

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

import scala.concurrent.Future

import com.github.mkroli.dss.dns.Message
import com.github.mkroli.dss.dns.akka.DnsActor
import com.github.mkroli.dss.dns.akka.DnsPacket
import com.github.mkroli.dss.dns.dsl.DnsMessage
import com.github.mkroli.dss.dns.dsl.ErrorMessage
import com.github.mkroli.dss.dns.section.QuestionSection
import com.github.mkroli.dss.dns.section.ResourceRecord
import com.github.mkroli.dss.dns.section.resource.AResource
import com.github.mkroli.dss.dns.section.resource.CNameResource

import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.pattern.pipe

trait DnsFrontendComponent {
  self: AkkaComponent with IndexComponent with ConfigurationComponent with AkkaMetricsComponent =>

  lazy val listenPort: Int = config.getInt("server.bind.port")
  lazy val fallbackDnsAddress = new InetSocketAddress(
    config.getString("server.fallback.address"),
    config.getInt("server.fallback.port"))

  lazy val dnsHandlerActor = actorSystem.actorOf(
    Props(new MetricActor(
      actorSystem.actorOf(Props(new DnsHandlerActor), "DnsHandlerActor"),
      timerName = Some("timer"),
      meterName = Some("meter"))))
  lazy val dnsActor = actorSystem.actorOf(Props(new DnsActor(listenPort, dnsHandlerActor, timeout)))

  class DnsHandlerActor extends Actor {
    private object SearchableQuestion {
      def unapply(message: Message) = message.question.find {
        case QuestionSection(_,
          ResourceRecord.typeA |
          ResourceRecord.typeAAAA,
          ResourceRecord.classIN) => true
        case _ => false
      }
    }

    private object ByteX {
      def unapply(s: String): Option[Byte] = try {
        Some(s.toByte)
      } catch {
        case _: Throwable => None
      }
    }

    private object IpAddress {
      val ipv4Regex = """(\d+)\.(\d+)\.(\d+)\.(\d+)""".r

      def unapply(s: String): Option[InetAddress] = s match {
        case ipv4Regex(ByteX(a), ByteX(b), ByteX(c), ByteX(d)) =>
          Some(InetAddress.getByAddress(Array(a, b, c, d)))
        case _ => None
      }
    }

    override def receive = {
      case originalRequest: Message =>
        dnsActor ? DnsPacket(originalRequest, fallbackDnsAddress) flatMap {
          case ErrorMessage(answer @ SearchableQuestion(question)) =>
            indexActor ? SearchIndex(question.qname.replace('.', ' ')) flatMap {
              case Some(IpAddress(addr: Inet4Address)) =>
                Future(DnsMessage(answer)
                  .withoutAnswers
                  .withAnswer(question.qname, AResource(addr))
                  .build)
              case Some(result: String) =>
                val lookup = DnsPacket(
                  DnsMessage.withQuestion(question.copy(qname = result)).build,
                  fallbackDnsAddress)
                dnsActor ? lookup map {
                  case answer: Message =>
                    DnsMessage(answer)
                      .withoutQuestions
                      .withoutAnswers
                      .withQuestionsFrom(originalRequest)
                      .withAnswer(question.qname, CNameResource(result))
                      .withAnswersFrom(answer)
                      .build
                }
              case _ => Future(answer)
            }
          case answer: Message => Future(answer)
        } pipeTo sender
    }
  }
}
