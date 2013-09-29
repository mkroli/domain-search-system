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
package com.github.mkroli.dss.dns.dsl

import com.github.mkroli.dss.dns.Message
import com.github.mkroli.dss.dns.MessageBuffer
import com.github.mkroli.dss.dns.section.HeaderSection
import com.github.mkroli.dss.dns.section.QuestionSection
import com.github.mkroli.dss.dns.section.Resource
import com.github.mkroli.dss.dns.section.ResourceRecord
import com.github.mkroli.dss.dns.section.resource.AResource
import com.github.mkroli.dss.dns.section.resource.CNameResource
import com.github.mkroli.dss.dns.section.resource.MXResource
import com.github.mkroli.dss.dns.section.resource.NSResource
import com.github.mkroli.dss.dns.section.resource.PTRResource
import com.github.mkroli.dss.dns.section.resource.SOAResource
import com.github.mkroli.dss.dns.section.resource.UnknownResource

class DnsMessage private (msg: Message) {
  def id(id: Int) = new DnsMessage(msg.copy(header = msg.header.copy(id = id)))

  def response(qr: Boolean = HeaderSection.qrResponse) = new DnsMessage(
    msg.copy(header = msg.header.copy(qr = qr)))

  def opcode(opcode: Int) = new DnsMessage(
    msg.copy(header = msg.header.copy(opcode = opcode)))

  def aa(aa: Boolean) = new DnsMessage(
    msg.copy(header = msg.header.copy(aa = aa)))

  def tc(tc: Boolean) = new DnsMessage(
    msg.copy(header = msg.header.copy(tc = tc)))

  def rd(rd: Boolean) = new DnsMessage(
    msg.copy(header = msg.header.copy(rd = rd)))

  def ra(ra: Boolean) = new DnsMessage(
    msg.copy(header = msg.header.copy(ra = ra)))

  def rcode(rcode: Int) = new DnsMessage(
    msg.copy(header = msg.header.copy(rcode = rcode)))

  def withQuestion(question: QuestionSection) = new DnsMessage(msg.copy(
    header = msg.header.copy(qdcount = msg.header.qdcount + 1),
    question = msg.question :+ question))

  def withoutQuestions() = new DnsMessage(msg.copy(
    header = msg.header.copy(qdcount = 0),
    question = Nil))

  private def resourceRecord[R <: Resource](
    name: String,
    resource: R,
    ttl: Long,
    `class`: Int) = {
    val `type` = resource match {
      case AResource(_) => ResourceRecord.typeA
      case NSResource(_) => ResourceRecord.typeNS
      case CNameResource(_) => ResourceRecord.typeCNAME
      case SOAResource(_, _, _, _, _, _, _) => ResourceRecord.typeSOA
      case PTRResource(_) => ResourceRecord.typePTR
      case MXResource(_, _) => ResourceRecord.typeMX
      case UnknownResource(_, t) => t
    }
    val buf = resource(MessageBuffer())
    buf.flip()
    new ResourceRecord(name, `type`, `class`, ttl, buf.remaining(), resource)
  }

  def withAnswer[R <: Resource](
    name: String,
    resource: R,
    ttl: Long = 0,
    `class`: Int = ResourceRecord.classIN) = {
    new DnsMessage(msg.copy(
      header = msg.header.copy(ancount = msg.header.ancount + 1),
      answer = msg.answer :+ resourceRecord(name, resource, ttl, `class`)))
  }

  def withoutAnswers() = new DnsMessage(msg.copy(
    header = msg.header.copy(ancount = 0),
    answer = Nil))

  def withAuthority[R <: Resource](
    name: String,
    resource: R,
    ttl: Long = 0,
    `class`: Int = ResourceRecord.classIN) = {
    new DnsMessage(msg.copy(
      header = msg.header.copy(nscount = msg.header.nscount + 1),
      authority = msg.authority :+ resourceRecord(name, resource, ttl, `class`)))
  }

  def withoutAuthority() = new DnsMessage(msg.copy(
    header = msg.header.copy(nscount = 0),
    authority = Nil))

  def withAdditional[R <: Resource](
    name: String,
    resource: R,
    ttl: Long = 0,
    `class`: Int = ResourceRecord.classIN) = {
    new DnsMessage(msg.copy(
      header = msg.header.copy(arcount = msg.header.arcount + 1),
      additional = msg.additional :+ resourceRecord(name, resource, ttl, `class`)))
  }

  def withoutAdditional() = new DnsMessage(msg.copy(
    header = msg.header.copy(arcount = 0),
    additional = Nil))

  def build() = msg
}

object DnsMessage extends DnsMessage(
  new Message(
    header = new HeaderSection(
      id = 0,
      qr = HeaderSection.qrQuery,
      opcode = HeaderSection.opcodeStandardQuery,
      aa = false,
      tc = false,
      rd = true,
      ra = false,
      rcode = HeaderSection.rcodeNoError,
      qdcount = 0,
      ancount = 0,
      nscount = 0,
      arcount = 0),
    question = Nil,
    answer = Nil,
    authority = Nil,
    additional = Nil)) {
  def apply(msg: Message) = new DnsMessage(msg)
}

object QueryMessage {
  def unapply(message: Message) = message.header.qr match {
    case HeaderSection.qrQuery => Some(message)
    case _ => None
  }
}

object ResponseMessage {
  def unapply(message: Message) = message.header.qr match {
    case HeaderSection.qrResponse => Some(message)
    case _ => None
  }
}

object ErrorMessage {
  def unapply(message: Message) = message.header.rcode match {
    case HeaderSection.rcodeNoError => None
    case _ => Some(message)
  }
}
