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
package com.github.mkroli.dss.dns.section

import java.nio.ByteBuffer

import com.github.mkroli.dss.dns.ByteBufferHelper

case class QuestionSection(
  qname: String,
  qtype: Int,
  qclass: Int) {
  def apply(bytes: ByteBuffer) = {
    bytes
      .putDomainName(qname)
      .putUnsignedInt(2, qtype)
      .putUnsignedInt(2, qclass)
  }
}

object QuestionSection {
  def apply(bytes: ByteBuffer) = {
    new QuestionSection(
      bytes.getDomainName(),
      bytes.getUnsignedInt(2),
      bytes.getUnsignedInt(2))
  }
}
