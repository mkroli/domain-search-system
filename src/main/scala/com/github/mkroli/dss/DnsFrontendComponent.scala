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
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions

import org.jboss.netty.bootstrap.ConnectionlessBootstrap
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory

import com.github.mkroli.dss.dns.Message
import com.github.mkroli.dss.dns.dsl.DnsMessage
import com.github.mkroli.dss.dns.netty.DnsDecoder
import com.github.mkroli.dss.dns.netty.DnsEncoder
import com.github.mkroli.dss.dns.section.HeaderSection
import com.github.mkroli.dss.dns.section.QuestionSection
import com.github.mkroli.dss.dns.section.ResourceRecord
import com.github.mkroli.dss.dns.section.resource.AResource
import com.github.mkroli.dss.dns.section.resource.CNameResource
import com.google.common.cache.CacheBuilder

import akka.pattern.ask

trait DnsFrontendComponent {
  self: AkkaComponent with SearchComponent with ConfigurationComponent =>

  lazy val listenPort: Int = config.getInt("server.bind.port")
  lazy val fallbackDnsAddress = new InetSocketAddress(
    config.getString("server.fallback.address"),
    config.getInt("server.fallback.port"))

  lazy val channel = {
    val f = new NioDatagramChannelFactory()
    val b = new ConnectionlessBootstrap(f)
    b.setPipelineFactory(new ChannelPipelineFactory {
      override def getPipeline() = Channels.pipeline(
        new DnsDecoder(),
        new DnsEncoder(),
        new DnsHandler())
    })
    b.setOption("receiveBufferSizePredictorFactory",
      new FixedReceiveBufferSizePredictorFactory(512))
    b.bind(new InetSocketAddress(listenPort))
  }

  class DnsHandler extends SimpleChannelUpstreamHandler {
    val idLock = new AnyRef
    @volatile var nextFreeId = 0

    val requests = JavaConversions.mapAsScalaMap(CacheBuilder.newBuilder()
      .expireAfterWrite(10, TimeUnit.SECONDS)
      .build[Integer, (SocketAddress, Message, Option[String])]()
      .asMap())

    private def findSearchableQuestion(dnsMessage: Message) = dnsMessage.question.find {
      case QuestionSection(_,
        ResourceRecord.typeA |
        ResourceRecord.typeAAAA,
        ResourceRecord.classIN) => true
      case _ => false
    }

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      e.getMessage match {
        case request: Message if request.header.qr == HeaderSection.qrQuery =>
          val id = idLock.synchronized {
            nextFreeId = (nextFreeId + 1) % 0x10000
            nextFreeId
          }
          requests.put(id, (e.getRemoteAddress(), request, None))
          channel.write(request.copy(header = request.header.copy(id = id)), fallbackDnsAddress)
        case response: Message =>
          def respond(response: Message, remote: SocketAddress) {
            requests.remove(response.header.id)
            channel.write(response, remote)
          }
          def pass(request: Message, remote: SocketAddress) {
            respond(response.copy(header = response.header.copy(id = request.header.id)), remote)
          }
          requests.get(response.header.id).foreach {
            case (remote, request, searchResult) if !request.question.isEmpty => (findSearchableQuestion(request), response) match {
              case (Some(searchableQuestion), response) if response.header.rcode == HeaderSection.rcodeNoError =>
                val baseResult = DnsMessage(response)
                  .id(request.header.id)
                  .withoutQuestions
                  .withoutAnswers
                val resultWithQuestions = request.question.foldLeft(baseResult) { (dnsMessage, question) =>
                  dnsMessage.withQuestion(question)
                }
                val resultWithSearchResult = searchResult.foldLeft(resultWithQuestions) { (dnsMessage, searchResult) =>
                  dnsMessage.withAnswer(searchableQuestion.qname, CNameResource(searchResult))
                }
                val resultWithAnswers = response.answer.foldLeft(resultWithSearchResult) { (dnsMessage, answer) =>
                  dnsMessage.withAnswer(answer.name, answer.rdata, answer.ttl, answer.`class`)
                }
                respond(resultWithAnswers.build(), remote)
              case (Some(searchableQuestion), response) =>
                searchActor ? searchableQuestion.qname.replace('.', ' ') onSuccess {
                  case Some(result: String) =>
                    requests.put(response.header.id, (remote, request, Some(result)))
                    channel.write(DnsMessage
                      .id(response.header.id)
                      .withQuestion(QuestionSection(result, searchableQuestion.qtype, searchableQuestion.qclass))
                      .build(),
                      fallbackDnsAddress)
                  case _ => pass(request, remote)
                }
              case _ => pass(request, remote)
            }
          }
      }
    }
  }
}
