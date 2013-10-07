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

object Build extends sbt.Build {
  lazy val projectSettings = Seq(
    name := "domain-search-system",
    organization := "com.github.mkroli",
    scalaVersion := "2.10.3",
    scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation"))

  lazy val projectDependencies = Seq(
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "15.0",
      "com.google.code.findbugs" % "jsr305" % "2.0.2" % "provided",
      "com.typesafe" % "config" % "1.0.2",
      "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
      "ch.qos.logback" % "logback-classic" % "1.0.13",
      "com.typesafe.akka" %% "akka-actor" % "2.2.1",
      "org.apache.lucene" % "lucene-core" % "4.5.0",
      "org.apache.lucene" % "lucene-analyzers-common" % "4.5.0",
      "org.apache.lucene" % "lucene-queryparser" % "4.5.0",
      "net.databinder" %% "unfiltered-netty" % "0.7.0",
      "net.databinder" %% "unfiltered-netty-server" % "0.7.0",
      "org.json4s" %% "json4s-native" % "3.2.5",
      "nl.grons" %% "metrics-scala" % "3.0.3"))

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
    JsKeys.variableRenamingPolicy in Compile := VariableRenamingPolicy.OFF,
    compile in Compile <<= compile in Compile dependsOn (JsKeys.js in Compile),
    sourceDirectory in (Compile, JsKeys.js) <<= (sourceDirectory in Compile) { d =>
      d / "javascript"
    },
    resourceManaged in (Compile, JsKeys.js) <<= (resourceManaged in Compile) { d =>
      d / "com/github/mkroli/dss/static/js"
    },
    resourceGenerators in Compile <+= (JsKeys.js in Compile),
    includeFilter in (Compile, JsKeys.js) := "*.jsm")

  lazy val projectLessSettings = Seq(
    compile in Compile <<= compile in Compile dependsOn (LessKeys.less in Compile),
    sourceDirectory in (Compile, LessKeys.less) <<= (sourceDirectory in Compile) { d =>
      d / "less"
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
      lessSettings ++
      projectLessSettings)
}
