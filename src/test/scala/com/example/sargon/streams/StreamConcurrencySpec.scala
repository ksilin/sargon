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

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{ BeforeAndAfterAll, FreeSpecLike, Matchers }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class StreamConcurrencySpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with Matchers
    with FreeSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("StreamConcurrencySpec"))

  // http://blog.akka.io/streams/2016/07/06/threading-and-concurrency-in-akka-streams-explained
  "exploring concurrency in akka streams" - {

    implicit val mat = ActorMaterializer()
    import system.dispatcher // implicit ec

    "do not block caller thread" in {
      println(s"${Thread.currentThread().getName}")

      // does not return future but rather NotUsed
      val completed: Future[Done] = Source
        .single("Hi")
        .map(_ + " stream world")
        .runForeach({ s =>
          println(s"${Thread.currentThread().getName}: $s")
        })

      println(s"${Thread.currentThread().getName} done")
      Await.result(completed, 1 second)
      // 'done' gets printed before the processed string does - stream runs on different thread
    }

    def processing(name: String): String => String = s => {
      println(s"$name started processing ${Thread.currentThread().getName} : $s")
      Thread.sleep(10)
      println(s"$name finished processing ${Thread.currentThread().getName} : $s")
      s + name
    }

    "keeping on single thread" in {

      val completed: Future[Done] = Source
        .single("Hi stream world")
        .map { processing(" bill") }
        .map { s =>
          println(s"${Thread.currentThread().getName} : $s"); s
        }
        .runWith(Sink.foreach(s => println(s"${Thread.currentThread().getName} : $s")))

      Await.result(completed, 1 second)
      // mostly but not always the same thread name for all stages
      completed.onComplete(x => println(s"done: $x"))
    }

    "using stages instead of lambdas - sync" in {
      def processingStage(name: String): Flow[String, String, NotUsed] =
        Flow[String].map(processing(name))

      // still all on same thread, running synchronously one string after another
      val completed: Future[Done] = Source(List("hi", "stream", "world"))
        .via(processingStage("X"))
        .via(processingStage("Y"))
        .via(processingStage("Z"))
        .runWith(Sink.foreach(s => println(s"received: $s")))
      Await.result(completed, 10 seconds)
      completed.onComplete(x => println(s"done: $x"))
    }

    "spreading over multiple threads with async" in {
      def processingStage(name: String): Flow[String, String, NotUsed] =
        Flow[String].map(processing(name))

      // stages run in parallel
      // final elements still arrive in same order
      val completed: Future[Done] = Source(List("hi", "stream", "world"))
        .via(processingStage("X"))
        .async
        .via(processingStage("Y"))
        .async
        .via(processingStage("Z"))
        .async
        .runWith(Sink.foreach(s => println(s"received: $s")))
      Await.result(completed, 10 seconds)
      completed.onComplete(x => println(s"done: $x"))
    }

  }
}
