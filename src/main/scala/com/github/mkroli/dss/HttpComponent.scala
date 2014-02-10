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

import org.json4s.DefaultFormats
import org.json4s.JObject
import org.json4s.JString

import akka.actor.Props
import akka.io.IO
import akka.pattern.ask
import spray.can.Http
import spray.http.StatusCodes
import spray.httpx.Json4sSupport
import spray.httpx.unmarshalling.BasicUnmarshallers
import spray.routing.HttpServiceActor

trait HttpComponent {
  self: ConfigurationComponent with AkkaComponent with IndexComponent =>

  IO(Http)(actorSystem) ? Http.Bind(
    actorSystem.actorOf(Props(new DssHttpServiceActor)),
    interface = config.getString("http.interface"),
    port = config.getInt("http.port"))

  class DssHttpServiceActor extends HttpServiceActor with BasicUnmarshallers with Json4sSupport {
    override implicit def json4sFormats = DefaultFormats

    lazy val hostRoute = path("api" / "host" / Segment) { s =>
      get {
        complete {
          (indexActor ? SearchIndex(s)).mapTo[Option[String]].map(_.map { host =>
            JObject("id" -> JString(host))
          })
        }
      } ~ put {
        entity(as[String]) { text =>
          complete {
            indexActor ! IndexItem(s, text)
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
          (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[IndexItem]]
        }
      } ~ post {
        entity(as[List[IndexItem]]) { index =>
          complete {
            index.foreach(indexActor ! _)
            StatusCodes.NoContent
          }
        }
      } ~ delete {
        complete {
          (indexActor ? GetAllDocuments(0, 65535)).mapTo[Seq[IndexItem]].foreach { docs =>
            docs.foreach { doc =>
              indexActor ! RemoveFromIndex(doc.id)
            }
          }
          StatusCodes.NoContent
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
