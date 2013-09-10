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
package com.github.mkroli.dss.dns.section.resource

import com.github.mkroli.dss.dns.MessageBuffer
import com.github.mkroli.dss.dns.section.Resource

case class UnknownResource(bytes: Seq[Byte], `type`: Int) extends Resource {
  def apply(buf: MessageBuffer) = buf.putBytes(bytes.size, bytes.toArray)
}

object UnknownResource {
  def apply(buf: MessageBuffer, size: Int, `type`: Int) =
    new UnknownResource(buf.getBytes(size), `type`)
}
