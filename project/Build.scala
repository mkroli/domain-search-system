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
import sbt._
import sbt.Keys._
import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import sbtrelease.ReleaseStateTransformations._
import xerial.sbt.Pack._
import com.untyped.sbtjs.Plugin._
import com.untyped.sbtless.Plugin._
import com.github.mkroli.webresources.WebResources._

object Build extends sbt.Build {
  lazy val projectSettings = Seq(
    name := "domain-search-system",
    organization := "com.github.mkroli",
    scalaVersion := "2.11.0",
    scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation"))

  lazy val projectDependencies = Seq(
    resolvers ++= Seq(
      "mkroli" at "http://dl.bintray.com/mkroli/maven",
      "spray repo" at "http://repo.spray.io"),
    libraryDependencies ++= Seq(
      "com.github.mkroli" %% "dns4s-core" % "0.3",
      "com.github.mkroli" %% "dns4s-akka" % "0.3",
      "com.typesafe" % "config" % "1.2.0",
      "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
      "ch.qos.logback" % "logback-classic" % "1.1.2",
      "com.typesafe.akka" %% "akka-actor" % "2.3.2",
      "org.apache.lucene" % "lucene-core" % "4.8.0",
      "org.apache.lucene" % "lucene-analyzers-common" % "4.8.0",
      "org.apache.lucene" % "lucene-queryparser" % "4.8.0",
      "io.spray" %% "spray-can" % "1.3.1-20140423",
      "io.spray" %% "spray-routing" % "1.3.1-20140423",
      "org.json4s" %% "json4s-native" % "3.2.9",
      "nl.grons" %% "metrics-scala" % "3.1.1"))

  lazy val projectWebResourceSettings = Seq(
    webResources ++= Map(
      "less/bootstrap.min.css" -> "http://netdna.bootstrapcdn.com/bootstrap/3.1.1/css/bootstrap.min.css",
      "less/bootstrap-theme.min.css" -> "http://netdna.bootstrapcdn.com/bootstrap/3.1.1/css/bootstrap-theme.min.css"))

  lazy val projectClasspathSettings = Seq(
    unmanagedSourceDirectories in Compile <++= baseDirectory { base =>
      Seq(
        base / "src" / "main" / "resources",
        base / "src" / "pack" / "etc")
    },
    unmanagedClasspath in Runtime <+= baseDirectory map { base =>
      Attributed.blank(base / "src" / "pack" / "etc")
    })

  lazy val projectPackSettings = Seq(
    packMain := Map("dss" -> "com.github.mkroli.dss.Boot"),
    packJvmOpts := Map("dss" -> Seq("-Dlogback.configurationFile=${PROG_HOME}/etc/logback.xml")),
    packExtraClasspath := Map("dss" -> Seq("${PROG_HOME}/etc")))

  lazy val projectJsSettings = Seq(
    packageBin in Compile <<= packageBin in Compile dependsOn (JsKeys.js in Compile),
    sourceDirectory in (Compile, JsKeys.js) <<= (sourceDirectory in Compile) { d =>
      d / "javascript"
    },
    resourceManaged in (Compile, JsKeys.js) <<= (resourceManaged in Compile) { d =>
      d / "com/github/mkroli/dss/static/js"
    },
    resourceGenerators in Compile <+= (JsKeys.js in Compile),
    includeFilter in (Compile, JsKeys.js) := "*.jsm")

  lazy val projectLessSettings = Seq(
    packageBin in Compile <<= packageBin in Compile dependsOn (LessKeys.less in Compile),
    LessKeys.less in Compile <<= LessKeys.less in Compile dependsOn (resolveWebResources in Compile),
    sourceDirectories in (Compile, LessKeys.less) <<= (sourceDirectory in Compile, webResourcesBase in Compile) { (d1, d2) =>
      Seq(d1 / "less", d2 / "less")
    },
    resourceManaged in (Compile, LessKeys.less) <<= (resourceManaged in Compile) { d =>
      d / "com/github/mkroli/dss/static/css"
    },
    resourceGenerators in Compile <+= (LessKeys.less in Compile))

  lazy val projectReleaseSettings = Seq(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      setNextVersion,
      commitNextVersion))

  lazy val dss = Project(
    id = "domain-search-system",
    base = file("."),
    settings = Defaults.defaultSettings ++
      projectSettings ++
      projectDependencies ++
      projectClasspathSettings ++
      packSettings ++
      projectPackSettings ++
      releaseSettings ++
      projectReleaseSettings ++
      jsSettings ++
      projectJsSettings ++
      webResourceSettings ++
      projectWebResourceSettings ++
      lessSettings ++
      projectLessSettings)
}
