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
package com.github.mkroli.dss.index

import java.io.Reader

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.PER_FIELD_REUSE_STRATEGY
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.core.LowerCaseFilter
import org.apache.lucene.analysis.core.StopAnalyzer
import org.apache.lucene.analysis.core.StopFilter
import org.apache.lucene.analysis.ngram.NGramTokenFilter
import org.apache.lucene.analysis.standard.StandardFilter
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.util.Version

class DssAnalyzer private (version: Version,
  filterFactory: (String) => DssAnalyzer.FilterFactory)
  extends Analyzer(PER_FIELD_REUSE_STRATEGY) {
  override def createComponents(fieldName: String, reader: Reader) = {
    val src = new StandardTokenizer(version, reader)
    new TokenStreamComponents(src, filterFactory(fieldName)(version, src))
  }
}

object DssAnalyzer {
  type FilterFactory = (Version, TokenStream) => TokenStream

  def filterChain(filters: FilterFactory*): FilterFactory = { (version, src) =>
    filters.foldLeft[TokenStream](src) { (ts, f) =>
      f(version, ts)
    }
  }
  val standard: FilterFactory = new StandardFilter(_, _)
  val lowercase: FilterFactory = new LowerCaseFilter(_, _)
  val stopwords: FilterFactory = new StopFilter(_, _, StopAnalyzer.ENGLISH_STOP_WORDS_SET)
  val ngram: FilterFactory = new NGramTokenFilter(_, _, 1, 2)

  def apply(version: Version)(filterFactory: (String) => FilterFactory) =
    new DssAnalyzer(version, filterFactory)
}
