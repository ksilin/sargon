/*
 * Copyright 2016 ksilin
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

package com.example.sargon

import akka.actor.{ Actor, ActorLogging, ActorRef }

// if supervisor has other plans for the data

class DataFetchWorker(ids: Set[Int], supervisor: ActorRef, client: ActorRef)
    extends Actor
    with ActorLogging {

  import context.dispatcher

  import scala.concurrent.duration._

  def receive = {
    case StartBatch => start()
  }

  def start(): Unit = {

    log.debug(s"Start fetching: $ids")
    ids.foreach { id =>
      val msg = generateDataForId(id)
      log.debug(s"Sending response chunk with id $id to ${ supervisor }")
      //          supervisor ! ChunkReady(msg)
      client ! DataChunk(msg)
    }
    log.debug(s"batch done: $ids")
    context.system.scheduler.scheduleOnce(100 millis, supervisor, BatchDone)
    context.stop(self)
  }

  def generateDataForId(id: Int) = {
    s"generated data for $id \n"
  }
}
