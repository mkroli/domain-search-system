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
package com.github.mkroli.dss.dns

import scala.language.implicitConversions

import com.github.mkroli.dss.dns.dsl.MessageModifier
import com.github.mkroli.dss.dns.dsl.QuestionSectionModifier
import com.github.mkroli.dss.dns.dsl.ResourceRecordModifier
import com.github.mkroli.dss.dns.section.HeaderSection
import com.github.mkroli.dss.dns.section.QuestionSection
import com.github.mkroli.dss.dns.section.Resource
import com.github.mkroli.dss.dns.section.ResourceRecord
import com.github.mkroli.dss.dns.section.resource.UnknownResource

package object dsl {
  implicit def boolean2UnitOption(b: Boolean) = if (b) Some() else None

  private[dsl] class PlainMessage(qr: Boolean) extends Message(header = new HeaderSection(
    id = 0,
    qr = qr,
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
    additional = Nil)

  implicit class ComposableMessage(val msg: Message) extends Message(msg.header, msg.question, msg.answer, msg.authority, msg.additional) {
    def ~(mm: MessageModifier) = new ComposableMessage(mm(msg))
  }

  def resourceRecordModifier(`type`: Int, resource: Resource) = new ResourceRecordModifier {
    val buf = resource(MessageBuffer())
    buf.flip()

    override def apply(rr: ResourceRecord) =
      rr.copy(`type` = `type`, rdlength = buf.remaining, rdata = resource)
  }

  implicit def questionSectionModifierToQuestionSection(qsm: QuestionSectionModifier): QuestionSection =
    qsm(QuestionSection("", ResourceRecord.typeA, ResourceRecord.classIN))

  implicit class QuestionSectionModifierString(name: String) extends QuestionSectionModifier with ResourceRecordModifier {
    override def apply(qs: QuestionSection) = qs.copy(qname = name)

    override def apply(rr: ResourceRecord) = rr.copy(name = name)
  }

  implicit def stringToQuestionSection(s: String): QuestionSection =
    questionSectionModifierToQuestionSection(QuestionSectionModifierString(s))

  implicit def resourceRecordModifierToResourceRecord(rrm: ResourceRecordModifier): ResourceRecord = {
    rrm(ResourceRecord(
      "",
      ResourceRecord.typeNULL,
      ResourceRecord.classIN,
      3600,
      0,
      UnknownResource(Nil, ResourceRecord.typeNULL)))
  }
}
