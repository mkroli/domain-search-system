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

import com.github.mkroli.dss.dns.akka.DnsActor
import com.github.mkroli.dss.dns.akka.DnsPacket
import com.github.mkroli.dss.dns.dsl.ARecord
import com.github.mkroli.dss.dns.dsl.Answers
import com.github.mkroli.dss.dns.dsl.CNameRecord
import com.github.mkroli.dss.dns.dsl.ClassIN
import com.github.mkroli.dss.dns.dsl.Dns
import com.github.mkroli.dss.dns.dsl.NameError
import com.github.mkroli.dss.dns.dsl.QName
import com.github.mkroli.dss.dns.dsl.Query
import com.github.mkroli.dss.dns.dsl.QuestionSectionModifierString
import com.github.mkroli.dss.dns.dsl.Questions
import com.github.mkroli.dss.dns.dsl.Response
import com.github.mkroli.dss.dns.dsl.TypeA
import com.github.mkroli.dss.dns.dsl.messageModifierToMessage
import com.github.mkroli.dss.dns.dsl.resourceRecordModifierToResourceRecord
import com.github.mkroli.dss.dns.dsl.stringToQuestionSection
import com.github.mkroli.dss.dns.dsl.~

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
      case Query(originalRequest) ~ Questions(questions) =>
        dnsActor ? DnsPacket(originalRequest, fallbackDnsAddress) flatMap {
          case Response(response) ~ NameError() ~ Questions(QName(qname) ~ TypeA() ~ ClassIN() :: Nil) =>
            indexActor ? SearchIndex(qname.replace('.', ' ')) flatMap {
              case Some(IpAddress(addr: Inet4Address)) =>
                Future(Dns(Response ~ Questions(questions: _*) ~ Answers(qname ~ ARecord(addr))))
              case Some(result: String) =>
                val lookup = DnsPacket(
                  Query ~ Questions(result),
                  fallbackDnsAddress)
                dnsActor ? lookup map {
                  case Response() ~ Answers(answers) =>
                    Dns(Response ~
                      Questions(questions: _*) ~
                      Answers(qname ~ CNameRecord(result)) ~
                      Answers(answers: _*))
                  case _ => response
                }
              case _ => Future(response)
            }
          case Response(response) => Future(response)
        } pipeTo sender
    }
  }
}
