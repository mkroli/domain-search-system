/*
 * Copyright 2013, 2014 Michael Krolikowski
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
import scala.util.Try

import com.github.mkroli.dns4s.akka.Dns
import com.github.mkroli.dns4s.dsl.ARecord
import com.github.mkroli.dns4s.dsl.Answers
import com.github.mkroli.dns4s.dsl.CNameRecord
import com.github.mkroli.dns4s.dsl.ClassIN
import com.github.mkroli.dns4s.dsl.ComposableMessage
import com.github.mkroli.dns4s.dsl.NameError
import com.github.mkroli.dns4s.dsl.NoError
import com.github.mkroli.dns4s.dsl.QName
import com.github.mkroli.dns4s.dsl.Query
import com.github.mkroli.dns4s.dsl.QuestionSectionModifierString
import com.github.mkroli.dns4s.dsl.Questions
import com.github.mkroli.dns4s.dsl.Response
import com.github.mkroli.dns4s.dsl.TypeA
import com.github.mkroli.dns4s.dsl.resourceRecordModifierToResourceRecord
import com.github.mkroli.dns4s.dsl.stringToQuestionSection
import com.github.mkroli.dns4s.dsl.~

import akka.actor.Actor
import akka.actor.Props
import akka.io.IO
import akka.pattern.ask
import akka.pattern.pipe

trait DnsFrontendComponent {
  self: AkkaComponent with IndexComponent with ConfigurationComponent with AkkaMetricsComponent =>

  lazy val listenPort: Int = config.getInt("server.bind.port")
  lazy val fallbackDnsAddress = new InetSocketAddress(
    config.getString("server.fallback.address"),
    config.getInt("server.fallback.port"))
  lazy val autoIndex = config.getBoolean("server.autoindex")

  lazy val dnsHandlerActor = actorSystem.actorOf(
    Props(new MetricActor(
      actorSystem.actorOf(Props(new DnsHandlerActor), "DnsHandlerActor"),
      timerName = Some("timer"),
      meterName = Some("meter"))))

  IO(Dns)(actorSystem) ? Dns.Bind(dnsHandlerActor, listenPort, timeout)

  class DnsHandlerActor extends Actor {
    lazy val dnsActor = IO(Dns)(actorSystem)

    private object ByteX {
      def unapply(s: String) = Try(s.toByte).toOption
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
        dnsActor ? Dns.DnsPacket(originalRequest, fallbackDnsAddress) flatMap {
          case Response(response) ~ NameError() ~ Questions(QName(qname) ~ TypeA() ~ ClassIN() :: Nil) =>
            indexActor ? SearchIndex(qname.replace('.', ' ')) flatMap {
              case Some(IpAddress(addr: Inet4Address)) =>
                Future(Response ~ Questions(questions: _*) ~ Answers(qname ~ ARecord(addr)))
              case Some(result: String) =>
                val lookup = Dns.DnsPacket(
                  Query ~ Questions(result),
                  fallbackDnsAddress)
                dnsActor ? lookup map {
                  case Response(_) ~ Answers(answers) =>
                    Response ~
                      Questions(questions: _*) ~
                      Answers(qname ~ CNameRecord(result)) ~
                      Answers(answers: _*)
                  case _ => response
                }
              case _ => Future(response)
            }
          case Response(response) ~ NoError() ~ Questions(QName(host) :: Nil) ~ Answers(_ :: _) if autoIndex =>
            (indexActor ? GetFromIndex(host)).mapTo[Option[String]].collect {
              case None => indexActor ! IndexItem(host, "")
            }
            Future(response)
          case Response(response) => Future(response)
        } pipeTo sender
    }
  }
}
