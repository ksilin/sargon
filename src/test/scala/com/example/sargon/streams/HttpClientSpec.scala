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

package com.example.sargon.streams

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, RunnableGraph, Sink, Source }
import akka.testkit.TestKit
import com.example.sargon.StopSystemAfterAll
import org.scalatest.{ FreeSpecLike, MustMatchers }

import scala.concurrent.Future
import scala.util.Try

class HttpClientSpec(_system: ActorSystem)
    extends TestKit(_system)
    with FreeSpecLike
    with MustMatchers
    with StopSystemAfterAll {

  def this() = this(ActorSystem("httpclientspec"))

  implicit val mat = ActorMaterializer()
  import system.dispatcher

  "pushing requests through streams" - {}

  // accessing the mat value
  val poolClientFlow = Http().cachedHostConnectionPool[Int]("akka.io")

  val graph: RunnableGraph[(HostConnectionPool, Future[(Try[HttpResponse], Int)])] =
    Source.single(HttpRequest(uri = "/") -> 42).viaMat(poolClientFlow)(Keep.right).toMat(Sink.head)(Keep.both)

  val (pool: HostConnectionPool, fut: Future[(Try[HttpResponse], Int)]) = graph.run()

  pool.shutdown()

}
