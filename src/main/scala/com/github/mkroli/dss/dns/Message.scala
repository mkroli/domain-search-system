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

import java.nio.ByteBuffer

import com.github.mkroli.dss.dns.section.HeaderSection
import com.github.mkroli.dss.dns.section.QuestionSection
import com.github.mkroli.dss.dns.section.ResourceRecord

case class Message(
  header: HeaderSection,
  question: Seq[QuestionSection],
  answer: Seq[ResourceRecord],
  authority: Seq[ResourceRecord],
  additional: Seq[ResourceRecord]) {
  def apply(): ByteBuffer = {
    val bytes = ByteBuffer.allocate(4096)
    header(bytes)
    question.map(_(bytes))
    answer.map(_(bytes))
    authority.map(_(bytes))
    additional.map(_(bytes))
    bytes
  }
}

object Message {
  def apply(bytes: ByteBuffer): Message = {
    val header = HeaderSection(bytes)
    new Message(
      header,
      (1 to header.qdcount).map(_ => QuestionSection(bytes)),
      (1 to header.ancount).map(_ => ResourceRecord(bytes)),
      (1 to header.nscount).map(_ => ResourceRecord(bytes)),
      (1 to header.arcount).map(_ => ResourceRecord(bytes)))
  }

  def unapply(bytes: ByteBuffer): Option[(HeaderSection, Seq[QuestionSection], Seq[ResourceRecord], Seq[ResourceRecord], Seq[ResourceRecord])] = {
    try {
      val message = Message(bytes)
      Some(message.header, message.question, message.answer, message.authority, message.additional)
    } catch {
      case t: Throwable => None
    }
  }
}
