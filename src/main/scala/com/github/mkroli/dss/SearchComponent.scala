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

import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask

trait SearchComponent {
  self: AkkaComponent with IndexComponent =>

  lazy val searchActor = actorSystem.actorOf(Props(new SearchActor))

  class SearchActor extends Actor {
    override def receive = {
      case query: String =>
        val s = sender
        (indexActor ? SearchIndex(query)).mapTo[Seq[String]].map(_.toList) onSuccess {
          case head :: _ => s ! Some(head)
          case _ => s ! None
        }
    }
  }
}
