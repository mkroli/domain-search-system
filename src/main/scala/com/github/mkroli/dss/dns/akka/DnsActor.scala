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
package com.github.mkroli.dss.dns.akka

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions

import com.github.mkroli.dss.dns.Message
import com.github.mkroli.dss.dns.MessageBuffer
import com.github.mkroli.dss.dns.dsl.QueryMessage
import com.github.mkroli.dss.dns.dsl.ResponseMessage
import com.google.common.cache.CacheBuilder

import akka.actor.Actor
import akka.actor.ActorRef
import akka.io.IO
import akka.io.Udp
import akka.pattern.ask
import akka.util.ByteString
import akka.util.Timeout

case class DnsPacket(message: Message, destination: InetSocketAddress)

class DnsActor(port: Int, handler: ActorRef, implicit val timeout: Timeout) extends Actor {
  import context.system
  import context.dispatcher

  val idLock = new AnyRef
  @volatile var nextFreeId = 0

  val requests = JavaConversions.mapAsScalaMap(CacheBuilder.newBuilder()
    .expireAfterWrite(timeout.duration.toMillis, TimeUnit.MILLISECONDS)
    .build[Integer, ActorRef]()
    .asMap())
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(port))

  override def receive = {
    case Udp.Bound(_) => context.become(ready(sender))
  }

  object MessageInByteString {
    def unapply(bs: ByteString): Option[Message] = try {
      Some(Message(MessageBuffer(bs.asByteBuffer)))
    } catch {
      case _: Throwable => None
    }
  }

  def ready(socket: ActorRef): Receive = {
    case DnsPacket(QueryMessage(message), destination) =>
      val id = idLock.synchronized {
        nextFreeId = (nextFreeId + 1) % 0x10000
        nextFreeId
      }
      requests.put(id, sender)
      socket ! Udp.Send(
        ByteString(message.copy(header = message.header.copy(id = id))().flippedBuf),
        destination)
    case Udp.Received(MessageInByteString(QueryMessage(message)), remote) =>
      handler ? message onSuccess {
        case ResponseMessage(response) =>
          socket ! Udp.Send(
            ByteString(response.copy(header = response.header.copy(id = message.header.id))().flippedBuf),
            remote)
      }
    case Udp.Received(MessageInByteString(ResponseMessage(message)), remote) =>
      requests.get(message.header.id).foreach { sender =>
        sender ! message
      }
    case Udp.Unbind => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }
}
