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
import org.json4s.JArray
import org.json4s.JField
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JsonDSL.WithBigDecimal.map2jvalue
import org.json4s.JsonDSL.WithBigDecimal.pair2jvalue
import org.json4s.JsonDSL.WithBigDecimal.seq2jvalue
import org.json4s.JsonDSL.WithBigDecimal.string2jvalue
import org.json4s.native.JsonMethods.compact
import org.json4s.native.JsonMethods.parse
import org.json4s.native.JsonMethods.render

import akka.actor.Props
import akka.io.IO
import akka.pattern.ask
import spray.can.Http
import spray.http.StatusCodes
import spray.routing.HttpServiceActor

trait HttpComponent {
  self: ConfigurationComponent with AkkaComponent with IndexComponent =>

  IO(Http)(actorSystem) ! Http.Bind(
    actorSystem.actorOf(Props(new DssHttpServiceActor)),
    "localhost",
    port = config.getInt("http.port"))

  class DssHttpServiceActor extends HttpServiceActor {
    lazy val hostRoute = path("api" / "host" / Segment) { s =>
      get {
        complete {
          (indexActor ? SearchIndex(s)).mapTo[Option[String]].map(_.map { host =>
            compact(render("id" -> host))
          })
        }
      } ~ put {
        entity(as[String]) { description =>
          complete {
            indexActor ! AddToIndex(s, description)
            StatusCodes.NoContent
          }
        }
      } ~ delete {
        complete {
          indexActor ! RemoveFromIndex(s)
          StatusCodes.NoContent
        }
      }
    }

    lazy val indexRoute = path("api" / "index") {
      get {
        complete {
          (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[Document]].map { docs =>
            compact(render(docs.map { doc =>
              doc.getFields().toList.map { field =>
                field.name() -> field.stringValue()
              }.toMap
            }))
          }
        }
      } ~ post {
        entity(as[String]) { index =>
          complete {
            try {
              for {
                JArray(items) <- parse(index)
                JObject(item) <- items
                JField("id", JString(id)) <- item
                JField("text", JString(text)) <- item
              } {
                indexActor ! AddToIndex(id, text)
              }
              StatusCodes.NoContent
            } catch {
              case _: Throwable => StatusCodes.BadRequest
            }
          }
        }
      } ~ delete {
        complete {
          (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[Document]].map { docs =>
            docs.foreach { doc =>
              indexActor ! RemoveFromIndex(doc.get("id"))
            }
            StatusCodes.NoContent
          }
        }
      }
    }

    lazy val resourcesRoute = get {
      pathSingleSlash {
        getFromResource("com/github/mkroli/dss/static/index.html")
      } ~ getFromResourceDirectory("com/github/mkroli/dss/static/")
    }

    override def receive = runRoute(hostRoute ~ indexRoute ~ resourcesRoute)
  }
}
