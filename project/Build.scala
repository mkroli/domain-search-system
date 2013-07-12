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

object Build extends sbt.Build {
  lazy val projectSettings = Seq(
    name := "domain-search-system",
    organization := "com.github.mkroli",
    scalaVersion := "2.10.2",
    scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation"))

  lazy val projectDependencies = Seq(
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "14.0.1",
      "com.google.code.findbugs" % "jsr305" % "2.0.1" % "provided",
      "com.typesafe" % "config" % "1.0.2",
      "com.typesafe.akka" %% "akka-actor" % "2.2.0",
      "io.netty" % "netty" % "3.6.6.Final",
      "org.apache.lucene" % "lucene-core" % "4.3.1",
      "org.apache.lucene" % "lucene-analyzers-common" % "4.3.1",
      "org.apache.lucene" % "lucene-queryparser" % "4.3.1"))

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
      projectReleaseSettings)
}
