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
package com.github.mkroli.dss.dns.netty

import java.util.{ List => JList }

import com.github.mkroli.dss.dns.Message

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.codec.MessageToMessageCodec

class DnsCodec extends MessageToMessageCodec[DatagramPacket, DnsPacket] {
  override def encode(ctx: ChannelHandlerContext, msg: DnsPacket, out: JList[AnyRef]) {
    val response = msg.content()()
    response.flip
    out.add(new DatagramPacket(
      Unpooled.copiedBuffer(response),
      msg.recipient,
      msg.sender))
  }

  override def decode(ctx: ChannelHandlerContext, msg: DatagramPacket, out: JList[AnyRef]) {
    out.add(DnsPacket(
      Message(msg.content.nioBuffer),
      msg.recipient,
      msg.sender))
  }
}
