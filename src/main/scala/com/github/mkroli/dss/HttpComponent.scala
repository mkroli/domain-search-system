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

import scala.collection.JavaConversions.asScalaBuffer
import org.apache.lucene.document.Document
import org.json4s.JsonDSL.WithBigDecimal._
import org.json4s.native.JsonMethods.compact
import org.json4s.native.JsonMethods.render
import com.github.mkroli.dss.http.Rewrite
import akka.actor.actorRef2Scala
import akka.pattern.ask
import unfiltered.kit.GZip
import unfiltered.netty.Http
import unfiltered.netty.Resources
import unfiltered.netty.async.Planify
import unfiltered.request.Body
import unfiltered.request.GET
import unfiltered.request.PUT
import unfiltered.request.Path
import unfiltered.request.Seg
import unfiltered.response.JsonContent
import unfiltered.response.NoContent
import unfiltered.response.ResponseString
import unfiltered.request.DELETE

trait HttpComponent {
  self: ConfigurationComponent with AkkaComponent with IndexComponent =>

  lazy val resourcesPlan = {
    val resources = new Resources(
      getClass.getResource("/com/github/mkroli/dss/static/"),
      cacheSeconds = 3600)

    Planify(GZip.async(Rewrite.async(resources.intent) {
      case GET(Path(Seg(Nil))) => "/index.html"
    }))
  }

  lazy val indexPlan = Planify(GZip.async {
    case req @ GET(Path(Seg("api" :: Nil))) =>
      (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[Document]].onSuccess {
        case docs =>
          val json = docs.map { doc =>
            doc.getFields().toList.map { field =>
              field.name() -> field.stringValue()
            }.toMap
          }
          req.respond(JsonContent ~> ResponseString(compact(render(json))))
      }
    case req @ PUT(Path(Seg("api" :: id :: Nil))) =>
      indexActor ! AddToIndex(id, Body.string(req))
      req.respond(NoContent)
    case req @ DELETE(Path(Seg("api" :: id :: Nil))) =>
      indexActor ! RemoveFromIndex(id)
      req.respond(NoContent)
  })

  Http.local(config.getInt("http.port"))
    .chunked(1048576)
    .plan(resourcesPlan)
    .plan(indexPlan)
    .start
}