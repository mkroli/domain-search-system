/*
 * Copyright 2013, 2014 Michael Krolikowski
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

import java.io.File
import java.lang.{Float => JFloat}

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.math.pow

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.simple.SimpleQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.RAMDirectory
import org.apache.lucene.util.Version

import com.github.mkroli.dss.index.DssAnalyzer
import com.github.mkroli.dss.index.DssAnalyzer.filterChain
import com.github.mkroli.dss.index.DssAnalyzer.lowercase
import com.github.mkroli.dss.index.DssAnalyzer.ngram
import com.github.mkroli.dss.index.DssAnalyzer.standard
import com.github.mkroli.dss.index.DssAnalyzer.stopwords

import akka.actor.Actor
import akka.actor.FSM
import akka.actor.Props
import akka.actor.Stash

case class IndexItem(id: String, text: String)
case class RemoveFromIndex(id: String)
case class GetFromIndex(id: String)
case class SearchIndex(query: String)
case class GetAllDocuments(start: Int, end: Int)

trait IndexComponent {
  self: ConfigurationComponent with AkkaComponent =>

  lazy val indexActor = actorSystem.actorOf(Props(new IndexActor))

  sealed abstract class State
  case object Committed extends State
  case object Uncommitted extends State

  class IndexActor extends Actor with Stash with FSM[State, Either[IndexWriter, (DirectoryReader, IndexSearcher)]] {
    val analyzer = DssAnalyzer(Version.LUCENE_47) {
      case f if f.endsWith("_fuzzy") => filterChain(standard, lowercase, ngram)
      case f => filterChain(standard, lowercase, stopwords)
    }
    val directory = config.getString("index.filename") match {
      case fn if fn.isEmpty => new RAMDirectory
      case fn => FSDirectory.open(new File(fn))
    }
    def indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer)
    val queryParser = new SimpleQueryParser(
      analyzer,
      List("domain", "host", "text")
        .zipWithIndex
        .toMap
        .mapValues(i => pow(2, i + 1).toFloat: JFloat)
        .flatMap {
          case (field, weight) =>
            List(field -> weight, s"${field}_fuzzy" -> (1f: JFloat))
        })
    val includeHostname = config.getBoolean("index.include.host")
    val includeDomain = config.getBoolean("index.include.domain")

    def addToIndex(indexWriter: IndexWriter, host: String, description: String) {
      val doc = new Document
      def addWithFuzzy(prefix: String, content: String, store: Field.Store) = {
        doc.add(new TextField(prefix, content, store))
        doc.add(new TextField(s"${prefix}_fuzzy", content, Field.Store.NO))
      }
      val (hostName, domainName) = host.split("""\.""").toList match {
        case hostName :: tail => (hostName, tail.mkString("."))
        case _ => ("", "")
      }
      removeFromIndex(indexWriter, host)
      doc.add(new StringField("id", host, Field.Store.YES))
      addWithFuzzy("text", description, Field.Store.YES)
      if (includeHostname) addWithFuzzy("host", hostName, Field.Store.NO)
      if (includeDomain) addWithFuzzy("domain", domainName, Field.Store.NO)
      indexWriter.addDocument(doc)
    }

    def removeFromIndex(indexWriter: IndexWriter, host: String) {
      indexWriter.deleteDocuments(new TermQuery(new Term("id", host)))
    }

    def getFromIndex(indexSearcher: IndexSearcher, host: String) = {
      indexSearcher.search(new TermQuery(new Term("id", host)), 1).scoreDocs.toSeq.map { d =>
        indexSearcher.doc(d.doc).get("text")
      }.headOption
    }

    def search(indexSearcher: IndexSearcher, query: String) = {
      val q = queryParser.parse(query.replace('.', ' '))
      indexSearcher.search(q, 1).scoreDocs.toSeq.map { d =>
        indexSearcher.doc(d.doc).get("id")
      }.headOption
    }

    def getAllDocs(indexSearcher: IndexSearcher, start: Int, end: Int) = {
      indexSearcher
        .search(new MatchAllDocsQuery, end)
        .scoreDocs
        .toSeq
        .drop(start)
        .map(_.doc)
        .map(indexSearcher.doc)
        .map(doc => IndexItem(doc.get("id"), doc.get("text")))
    }

    def uncommitted = Left(new IndexWriter(directory, indexWriterConfig))

    def committed = {
      val indexReader = DirectoryReader.open(directory)
      Right(indexReader, new IndexSearcher(indexReader))
    }

    startWith(Uncommitted, uncommitted)

    when(Committed) {
      case Event((IndexItem(_, _) | RemoveFromIndex(_)), Right((indexReader, _))) =>
        stash
        indexReader.close()
        goto(Uncommitted) using uncommitted
      case Event(GetFromIndex(id), Right((_, indexSearcher))) =>
        sender ! getFromIndex(indexSearcher, id)
        stay
      case Event(SearchIndex(query), Right((_, indexSearcher))) =>
        sender ! search(indexSearcher, query)
        stay
      case Event(GetAllDocuments(start, end), Right((_, indexSearcher))) =>
        sender ! getAllDocs(indexSearcher, start, end)
        stay
    }

    when(Uncommitted, stateTimeout = 1 second) {
      case Event(IndexItem(id, text), Left(indexWriter)) =>
        addToIndex(indexWriter, id, text)
        stay
      case Event(RemoveFromIndex(id), Left(indexWriter)) =>
        removeFromIndex(indexWriter, id)
        stay
      case Event(msg @ (GetFromIndex(_) | SearchIndex(_) | GetAllDocuments(_, _) | StateTimeout), Left(indexWriter)) =>
        if (msg != StateTimeout) stash
        indexWriter.close()
        goto(Committed) using committed
    }

    onTransition {
      case _ -> Committed => log.info("committed")
    }

    onTransition {
      case _ => unstashAll
    }

    initialize()
  }
}
