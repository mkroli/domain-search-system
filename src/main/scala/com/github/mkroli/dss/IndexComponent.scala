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

import scala.collection.JavaConversions.mapAsJavaMap
import scala.language.postfixOps

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory
import org.apache.lucene.util.Version

import akka.actor.Actor
import akka.actor.FSM
import akka.actor.Props
import akka.actor.actorRef2Scala

trait IndexComponent {
  self: ConfigurationComponent with AkkaComponent =>

  lazy val indexActor = actorSystem.actorOf(Props(new IndexActor))

  case class AddToIndex(host: String, description: String)
  case class SearchIndex(query: String)

  sealed abstract class State
  case object Committed extends State
  case object Uncommitted extends State

  class IndexActor extends Actor with FSM[State, Either[IndexWriter, (DirectoryReader, IndexSearcher)]] {
    val analyzer = new StandardAnalyzer(Version.LUCENE_44)
    val directory = new RAMDirectory
    val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_44, analyzer)
    val queryParser = new MultiFieldQueryParser(Version.LUCENE_44,
      "text" :: "host" :: "domain" :: Nil toArray,
      analyzer,
      mapAsJavaMap(Map("text" -> 1.0f, "host" -> 0.75f, "domain" -> 0.5f)))
    val includeHostname = config.getBoolean("index.include.host")
    val includeDomain = config.getBoolean("index.include.domain")

    def addToIndex(indexWriter: IndexWriter, host: String, description: String) {
      val (hostName, domainName) = host.split("""\.""").toList match {
        case hostName :: tail => (hostName, tail.mkString("."))
        case _ => ("", "")
      }
      val doc = new Document
      doc.add(new Field("id", host, TextField.TYPE_STORED))
      doc.add(new Field("text", description, TextField.TYPE_NOT_STORED))
      if (includeHostname) doc.add(new Field("host", hostName, TextField.TYPE_NOT_STORED))
      if (includeDomain) doc.add(new Field("domain", domainName, TextField.TYPE_NOT_STORED))
      indexWriter.addDocument(doc)
    }

    def search(indexSearcher: IndexSearcher, query: String) = {
      val q = queryParser.parse(query)
      indexSearcher.search(q, 3).scoreDocs.toSeq.map { d =>
        indexSearcher.doc(d.doc).get("id")
      }
    }

    startWith(Uncommitted, Left(new IndexWriter(directory, indexWriterConfig)))

    when(Committed) {
      case Event(msg @ AddToIndex(host, description), Right((indexReader, _))) =>
        indexReader.close()
        val indexWriter = new IndexWriter(directory, indexWriterConfig)
        addToIndex(indexWriter, host, description)
        goto(Uncommitted) using Left(indexWriter)
      case Event(SearchIndex(query), Right((_, indexSearcher))) =>
        sender ! search(indexSearcher, query)
        stay
    }

    when(Uncommitted) {
      case Event(AddToIndex(host, description), Left(indexWriter)) =>
        addToIndex(indexWriter, host, description)
        stay
      case Event(SearchIndex(query), Left(indexWriter)) =>
        indexWriter.close()
        val indexReader = DirectoryReader.open(directory)
        val indexSearcher = new IndexSearcher(indexReader)
        sender ! search(indexSearcher, query)
        goto(Committed) using Right((indexReader, indexSearcher))
    }
  }
}
