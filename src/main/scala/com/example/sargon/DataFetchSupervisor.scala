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

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object DataStart

object DataEnd

object StartBatch

object BatchDone

case class DataChunk(data: String)

case class ChunkReady(data: String)

class DataFetchSupervisor(entity: String,
                          client: ActorRef,
                          startId: Int,
                          batchSize: Int)
    extends Actor
    with ActorLogging {
  log.debug("Starting data fetch supervisor ...")

  val finalId: Int = startId + batchSize - 1

  val maxSize = 10

  val idGroups = (startId to finalId).grouped(maxSize)

  client ! DataStart

  var children = idGroups map { group =>
    context actorOf Props(new DataFetchWorker(group.to[Set], self, client))
  }
  children foreach { _ ! StartBatch }

  def receive = {

    case ChunkReady(data) => {
      log.info(s"forwarding chunk: $data")
      sendChunk(data)
    }

    case BatchDone => {
      log.info(s"received BatchDone from $sender")

      children = children.filter(_ != sender)

      if (children.isEmpty) {
        log.info("all children are done, waiting for termination")
        //        context.system.scheduler.scheduleOnce(10 second, client, ChunkedMessageEnd)
        //        context.stop(self)
      }

    }
  }

  private def sendChunk(jsonString: String): Unit = {
    log.debug(s"Sending response chunk to client")
    client ! DataChunk(jsonString)
  }
}
