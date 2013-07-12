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

case class HeaderSection(
  id: Int,
  qr: Boolean,
  opcode: Int,
  aa: Boolean,
  tc: Boolean,
  rd: Boolean,
  ra: Boolean,
  rcode: Int,
  qdcount: Int,
  ancount: Int,
  nscount: Int,
  arcount: Int) {
  def apply(bytes: ByteBuffer) = {
    val tmpQr = if (qr) 1 << 15 else 0
    val tmpOpcode = (opcode & 15) << 11
    val tmpAa = if (aa) 1 << 10 else 0
    val tmpTc = if (tc) 1 << 9 else 0
    val tmpRd = if (rd) 1 << 8 else 0
    val tmpRa = if (ra) 1 << 7 else 0
    val tmpRcode = rcode & 15
    val tmp = tmpQr | tmpOpcode | tmpAa | tmpTc | tmpRd | tmpRa | tmpRcode

    bytes
      .putUnsignedInt(2, id)
      .putUnsignedInt(2, tmp)
      .putUnsignedInt(2, qdcount)
      .putUnsignedInt(2, ancount)
      .putUnsignedInt(2, nscount)
      .putUnsignedInt(2, arcount)
  }
}

object HeaderSection {
  val qrQuery = false
  val qrResponse = true

  val opcodeStandardQuery = 0
  val opcodeInverseQuery = 1
  val opcodeServerStatusRequest = 2

  val rcodeNoError = 0
  val rcodeFormatError = 1
  val rcodeServerFailure = 2
  val rcodeNameError = 3
  val rcodeNotImplemented = 4
  val rcodeRefused = 5

  def apply(bytes: ByteBuffer) = {
    val id = bytes.getUnsignedInt(2)
    val tmp = bytes.getUnsignedInt(2)
    new HeaderSection(
      id = id,
      qr = (tmp & (1 << 15)) != 0,
      opcode = (tmp >>> 11) & 15,
      aa = (tmp & (1 << 10)) != 0,
      tc = (tmp & (1 << 9)) != 0,
      rd = (tmp & (1 << 8)) != 0,
      ra = (tmp & (1 << 7)) != 0,
      rcode = tmp & 15,
      qdcount = bytes.getUnsignedInt(2),
      ancount = bytes.getUnsignedInt(2),
      nscount = bytes.getUnsignedInt(2),
      arcount = bytes.getUnsignedInt(2))
  }
}
