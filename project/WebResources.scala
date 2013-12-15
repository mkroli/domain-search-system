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
package com.github.mkroli.webresources

import java.io._
import org.apache.commons.io._
import sbt._
import sbt.Keys._

object WebResources extends Plugin {
  val webResources = settingKey[Map[String, String]]("Mapping from file to url")

  val webResourcesBase = settingKey[File]("Base folder of downloaded files")

  val resolveWebResources = taskKey[Seq[File]]("Download files")

  lazy val webResourceSettings = Seq(
    webResources := Map(),
    webResourcesBase := target.value / "webresources",
    resolveWebResources <<= (streams, webResources, webResourcesBase) map {
      (streams, webResources, webResourcesBase) =>
        webResources.par.map {
          case (filename, url) => (webResourcesBase / filename) -> new URL(url)
        }.filterKeys(!_.exists).map {
          case (file, url) =>
            streams.log.info(s"Downloading ${file.getName}")
            FileUtils.copyURLToFile(url, file, 10000, 10000)
            file
        }.seq.toSeq
    })
}
