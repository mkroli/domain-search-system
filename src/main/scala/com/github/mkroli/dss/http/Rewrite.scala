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
package com.github.mkroli.dss.http

import unfiltered.Async
import unfiltered.Cycle
import unfiltered.request.DelegatingRequest
import unfiltered.request.HttpRequest
import unfiltered.response.Pass
import unfiltered.response.ResponseFunction

object Rewrite {
  def apply[A, B](intent: Cycle.Intent[A, B])(rules: PartialFunction[HttpRequest[A], String]) = {
    Cycle.Intent[A, B] {
      case req => Cycle.Intent.complete(intent)(new DelegatingRequest(req) {
        override def uri = rules.lift(req).getOrElse(req.uri)
      })
    }
  }

  def async[A, B](intent: Async.Intent[A, B])(rules: PartialFunction[HttpRequest[A], String]) = {
    Async.Intent[A, B] {
      case req => intent.lift(new DelegatingRequest(req) with Async.Responder[B] {
        override def uri = rules.lift(req).getOrElse(req.uri)

        override def respond(rf: ResponseFunction[B]) = req.respond(rf)
      }).getOrElse(Pass)
    }
  }
}
