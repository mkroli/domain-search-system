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

trait IndexComponent {
  self: ConfigurationComponent =>

  lazy val analyzer = new StandardAnalyzer(Version.LUCENE_43)
  lazy val directory = new RAMDirectory
  lazy val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_43, analyzer)
  lazy val queryParser = new MultiFieldQueryParser(Version.LUCENE_43,
    "text" :: "host" :: "domain" :: Nil toArray,
    analyzer,
    mapAsJavaMap(Map("text" -> 1.0f, "host" -> 0.75f, "domain" -> 0.5f)))
  lazy val includeHostname = config.getBoolean("index.include.host")
  lazy val includeDomain = config.getBoolean("index.include.domain")

  def addToIndex(host: String, description: String) = {
    val (hostName, domainName) = host.split("""\.""").toList match {
      case hostName :: tail => (hostName, tail.mkString("."))
      case _ => ("", "")
    }
    val indexWriter = new IndexWriter(directory, indexWriterConfig)
    val doc = new Document
    doc.add(new Field("id", host, TextField.TYPE_STORED))
    doc.add(new Field("text", description, TextField.TYPE_NOT_STORED))
    if (includeHostname) doc.add(new Field("host", hostName, TextField.TYPE_NOT_STORED))
    if (includeDomain) doc.add(new Field("domain", domainName, TextField.TYPE_NOT_STORED))
    indexWriter.addDocument(doc)
    indexWriter.close()
  }

  def search(query: String) = {
    val indexReader = DirectoryReader.open(directory)
    val indexSearcher = new IndexSearcher(indexReader)
    val q = queryParser.parse(query)
    val result = indexSearcher.search(q, 3).scoreDocs.toSeq.map { d =>
      indexSearcher.doc(d.doc).get("id")
    }
    indexReader.close()
    result
  }
}
