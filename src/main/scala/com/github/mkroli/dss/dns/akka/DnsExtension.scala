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
import scala.collection.mutable.Map
import scala.concurrent.duration.DurationInt

import com.github.mkroli.dss.dns.Message
import com.github.mkroli.dss.dns.MessageBuffer
import com.github.mkroli.dss.dns.dsl.Query
import com.github.mkroli.dss.dns.dsl.Response
import com.google.common.cache.CacheBuilder

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.Props
import akka.io.IO
import akka.io.IO.Extension
import akka.io.Udp
import akka.pattern.ask
import akka.util.ByteString
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout

class DnsExtension(system: ExtendedActorSystem) extends Extension {
  override val manager = system.actorOf(Props[DnsExtensionActor])
}

object Dns extends ExtensionId[DnsExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem) = new DnsExtension(system)

  override def lookup = Dns

  case class Bind(handler: ActorRef, port: Int, implicit val timeout: Timeout = 5 seconds)

  case class DnsPacket(message: Message, destination: InetSocketAddress)
}

class DnsExtensionActor extends Actor {
  import context.system
  import context.dispatcher

  val idLock = new AnyRef
  @volatile var nextFreeId = 0

  override def receive = {
    case Dns.Bind(handler, port, timeout) =>
      IO(Udp) ! Udp.Bind(self, new InetSocketAddress(port))
      context.become(binding(handler)(timeout))
  }

  def binding(handler: ActorRef)(implicit timeout: Timeout): Receive = {
    case Udp.Bound(_) =>
      context.become(ready(sender, handler, JavaConversions.mapAsScalaMap(CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build[Integer, ActorRef]()
        .asMap())))
  }

  object MessageInByteString {
    def unapply(bs: ByteString): Option[Message] = try {
      Some(Message(MessageBuffer(bs.asByteBuffer)))
    } catch {
      case _: Throwable => None
    }
  }

  def ready(socket: ActorRef, handler: ActorRef, requests: Map[Integer, ActorRef])(implicit timeout: Timeout): Receive = {
    case Dns.DnsPacket(Query(message), destination) =>
      val id = idLock.synchronized {
        nextFreeId = (nextFreeId + 1) % 0x10000
        nextFreeId
      }
      requests.put(id, sender)
      socket ! Udp.Send(
        ByteString(message.copy(header = message.header.copy(id = id))().flippedBuf),
        destination)
    case Udp.Received(MessageInByteString(Query(message)), remote) =>
      handler ? message onSuccess {
        case Response(response) =>
          socket ! Udp.Send(
            ByteString(response.copy(header = response.header.copy(id = message.header.id))().flippedBuf),
            remote)
      }
    case Udp.Received(MessageInByteString(Response(message)), remote) =>
      requests.get(message.header.id).foreach { sender =>
        sender ! message
      }
    case Udp.Unbind => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }
}
