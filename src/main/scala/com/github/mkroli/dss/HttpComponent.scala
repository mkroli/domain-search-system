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
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.json4s.JArray
import org.json4s.JField
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JsonDSL.WithBigDecimal._
import org.json4s.jvalue2monadic
import org.json4s.native.JsonMethods.compact
import org.json4s.native.JsonMethods.parse
import org.json4s.native.JsonMethods.render
import org.json4s.string2JsonInput

import com.github.mkroli.dss.http.Rewrite

import akka.actor.actorRef2Scala
import akka.pattern.ask
import unfiltered.kit.GZip
import unfiltered.netty.Http
import unfiltered.netty.Resources
import unfiltered.netty.async.Planify
import unfiltered.request.Body
import unfiltered.request.DELETE
import unfiltered.request.GET
import unfiltered.request.POST
import unfiltered.request.PUT
import unfiltered.request.Path
import unfiltered.request.Seg
import unfiltered.response.BadRequest
import unfiltered.response.JsonContent
import unfiltered.response.NoContent
import unfiltered.response.NotFound
import unfiltered.response.ResponseString

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

  lazy val hostPlan = Planify(GZip.async {
    case req @ PUT(Path(Seg("api" :: "host" :: id :: Nil))) =>
      indexActor ! AddToIndex(id, Body.string(req))
      req.respond(NoContent)
    case req @ DELETE(Path(Seg("api" :: "host" :: id :: Nil))) =>
      indexActor ! RemoveFromIndex(id)
      req.respond(NoContent)
    case req @ GET(Path(Seg("api" :: "host" :: search :: Nil))) =>
      (indexActor ? SearchIndex(search)).mapTo[Option[String]].onSuccess {
        case Some(result) => req.respond(ResponseString(result))
        case None => req.respond(NotFound)
      }
  })

  lazy val indexPlan = Planify(GZip.async {
    case req @ GET(Path(Seg("api" :: "index" :: Nil))) =>
      (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[Document]].onSuccess {
        case docs =>
          val json = docs.map { doc =>
            doc.getFields().toList.map { field =>
              field.name() -> field.stringValue()
            }.toMap
          }
          req.respond(JsonContent ~> ResponseString(compact(render(json))))
      }
    case req @ POST(Path(Seg("api" :: "index" :: Nil))) =>
      try {
        for {
          JArray(items) <- parse(Body.string(req))
          JObject(item) <- items
          JField("id", JString(id)) <- item
          JField("text", JString(text)) <- item
        } {
          indexActor ! AddToIndex(id, text)
        }
        req.respond(NoContent)
      } catch {
        case _: Throwable => req.respond(BadRequest)
      }
    case req @ DELETE(Path(Seg("api" :: "index" :: Nil))) =>
      (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[Document]].onSuccess {
        case docs =>
          docs.foreach { doc =>
            indexActor ! RemoveFromIndex(doc.get("id"))
          }
          req.respond(NoContent)
      }
  })

  lazy val http = Http.local(config.getInt("http.port"))
    .chunked(1048576)
    .plan(resourcesPlan)
    .plan(hostPlan)
    .plan(indexPlan)
    .makePlan(new ChunkedWriteHandler)
    .start
}
