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
import akka.actor.ActorRef
import akka.pattern.ask
import akka.pattern.pipe

trait AkkaMetricsComponent {
  self: AkkaComponent with MetricsComponent =>

  class MetricActor(
    delegate: ActorRef,
    timerName: Option[String],
    meterName: Option[String]) extends Actor with Instrumented {
    val timer = timerName.map(name => metrics.timer(delegate.path.name, name))
    val meter = meterName.map(name => metrics.meter(delegate.path.name, name))

    override def receive = {
      case request =>
        meter.foreach(_.mark)
        val timerContext = timer.map(_.timerContext)
        delegate ? request pipeTo sender onSuccess {
          case _ => timerContext.foreach(_.stop)
        }
    }
  }
}
