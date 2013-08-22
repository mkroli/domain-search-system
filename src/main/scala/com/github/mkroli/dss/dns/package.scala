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

import java.nio.ByteBuffer

import scala.language.postfixOps
import scala.math.BigInt

package object dns {
  implicit class ByteBufferHelper(buf: ByteBuffer) {
    def getBytes(bytes: Int) = {
      require(bytes >= 0)
      (0 until bytes).map(_ => buf.get())
    }

    def putBytes(bytes: Int, a: Array[Byte]) = {
      require(a.length <= bytes)
      (0 until (bytes - a.length)).foreach(_ => buf.put(0: Byte))
      buf.put(a)
    }

    def getSignedBigInt(bytes: Int) =
      BigInt(getBytes(bytes) toArray)

    def putSignedBigInt(bytes: Int, i: BigInt) =
      putBytes(bytes, i.toByteArray)

    def getUnsignedBigInt(bytes: Int) =
      BigInt((0: Byte) +: getBytes(bytes) toArray)

    def putUnsignedBigInt(bytes: Int, i: BigInt) = {
      putBytes(bytes, i.toByteArray.toList match {
        case 0 :: tail => tail.toArray
        case a => a.toArray
      })
    }

    def getSignedLong(bytes: Int) = {
      require(bytes > 0 && bytes <= 8)
      getSignedBigInt(bytes).toLong
    }

    def putSignedLong(bytes: Int, l: Long) = {
      require(bytes > 0 && bytes <= 8)
      putSignedBigInt(bytes, l)
    }

    def getUnsignedLong(bytes: Int) = {
      require(bytes > 0 && bytes < 8)
      getUnsignedBigInt(bytes).toLong
    }

    def putUnsignedLong(bytes: Int, l: Long) = {
      require(bytes > 0 && bytes < 8)
      putUnsignedBigInt(bytes, l)
    }

    def getSignedInt(bytes: Int) = {
      require(bytes > 0 && bytes <= 4)
      getSignedBigInt(bytes).toInt
    }

    def putSignedInt(bytes: Int, i: Int) = {
      require(bytes > 0 && bytes <= 4)
      putSignedBigInt(bytes, i)
    }

    def getUnsignedInt(bytes: Int) = {
      require(bytes > 0 && bytes < 4)
      getUnsignedBigInt(bytes).toInt
    }

    def putUnsignedInt(bytes: Int, i: Int) = {
      require(bytes > 0 && bytes < 4)
      putUnsignedBigInt(bytes, i)
    }

    // FIXME: following is buggy when it comes to DNS compression
    def getDomainName(): String = {
      def getDomainNamePart(): String = {
        getUnsignedInt(1) match {
          case s if (s & 0xC0) != 0 =>
            buf.position(buf.position() - 1)
            val ptr = getUnsignedInt(2) - 0xC000
            val pos = buf.position()
            buf.position(ptr)
            val dn = getDomainName()
            buf.position(pos)
            dn
          case s if s == 0 => ""
          case s => (0 until s).map(_ => buf.get().toChar).mkString + "." + getDomainNamePart()
        }
      }
      getDomainNamePart()
    }

    def putDomainName(dn: String) = {
      buf.put(dn.split("""\.""")
        .filterNot(_.isEmpty)
        .flatMap(s => s.size.toByte +: s.getBytes))
        .put(0: Byte)
    }
  }
}
