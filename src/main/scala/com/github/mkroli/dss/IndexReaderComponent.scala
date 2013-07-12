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

import scala.io.Source
import scala.language.postfixOps

trait IndexReaderComponent {
  self: IndexComponent =>

  private val dssHosts = """([^ \t]+)(?:[ \t]+(.+))?"""r

  Source.fromInputStream(getClass.getResourceAsStream("/dss.hosts")).getLines.foreach {
    case dssHosts(host, null) => addToIndex(host, "")
    case dssHosts(host, description) => addToIndex(host, description)
    case line => println("Couldn't parse %s".format(line))
  }
}
