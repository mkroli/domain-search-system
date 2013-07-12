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
import com.github.mkroli.dss.dns.section.resource.AResource
import com.github.mkroli.dss.dns.section.resource.CNameResource
import com.github.mkroli.dss.dns.section.resource.NSResource
import com.github.mkroli.dss.dns.section.resource.UnknownResource

case class ResourceRecord(
  name: String,
  `type`: Int,
  `class`: Int,
  ttl: Long,
  rdlength: Int,
  rdata: Resource) {
  def apply(bytes: ByteBuffer) = {
    val data = rdata(ByteBuffer.allocate(512))
    data.flip
    val header = bytes
      .putDomainName(name)
      .putUnsignedInt(2, `type`)
      .putUnsignedInt(2, `class`)
      .putUnsignedLong(4, ttl)
      .putUnsignedInt(2, rdlength)
    (0 until data.remaining()).foldLeft(header) { (buf, i) =>
      buf.put(data.get())
    }
  }
}

object ResourceRecord {
  val typeA = 1
  val typeNS = 2
  val typeMD = 3
  val typeMF = 4
  val typeCNAME = 5
  val typeSOA = 6
  val typeMB = 7
  val typeMG = 8
  val typeMR = 9
  val typeNULL = 10
  val typeWKS = 11
  val typePTR = 12
  val typeHINFO = 13
  val typeMINFO = 14
  val typeMX = 15
  val typeTXT = 16
  val typeAAAA = 28
  val qtypeAXFR = 252
  val qtypeMAILB = 253
  val qtypeMAILA = 254
  val qtypeAsterisk = 255

  val classIN = 1
  val classCS = 2
  val classCH = 3
  val classHS = 4
  val qclassAsterisk = 255

  def apply(bytes: ByteBuffer) = {
    val name = bytes.getDomainName()
    val `type` = bytes.getUnsignedInt(2)
    val `class` = bytes.getUnsignedInt(2)
    val ttl = bytes.getUnsignedLong(4)
    val rdlength = bytes.getUnsignedInt(2)
    val rdata = `type` match {
      case 1 => AResource(bytes)
      case 2 => NSResource(bytes)
      case 5 => CNameResource(bytes)
      case _ => UnknownResource(bytes, rdlength, `type`)
    }
    new ResourceRecord(name, `type`, `class`, ttl, rdlength, rdata)
  }
}
