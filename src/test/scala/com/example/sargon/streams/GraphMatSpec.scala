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
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Keep, Source }
import akka.testkit.{ ImplicitSender, TestKit }
import akka.{ Done, NotUsed }
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ FreeSpecLike, MustMatchers }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

// http://doc.akka.io/docs/akka/current/scala/stream/stream-graphs.html#Accessing_the_materialized_value_inside_the_Graph
// http://stackoverflow.com/questions/35516519/how-to-test-an-akka-stream-closed-shape-runnable-graph-with-an-encapsulated-sour

//http://stackoverflow.com/questions/37911174/via-viamat-to-tomat-in-akka-stream
//http://stackoverflow.com/questions/35818358/akka-stream-tomat
//http://stackoverflow.com/questions/34924434/akka-streams-how-to-access-the-materialized-value-of-the-stream

class GraphMatSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with FreeSpecLike
    with MustMatchers
    with LazyLogging {

  def this() = this(ActorSystem("StreamConcurrencySpec"))

  implicit val mat = ActorMaterializer() // implicit ec

  "deciphering the *Mat methods" - {

    val doubleFlow: Flow[Int, Int, NotUsed] = Flow[Int].mapAsync(4) { i =>
      Future.successful(i * 2)
    }

    val justPrint: (Int) => Unit = { s =>
      println(s"done $s")
    }

//    via(flow) = viaMap(flow)(Keep.*)

    // *Mat methods are used in case you want to manipulate the meterialized graph, like ingest data

    "via" in {
      val done: Future[Done] = Source(1 to 10).via(doubleFlow).runForeach(justPrint)
      Await.result(done, 1 second)
    }

    "viaMat" in {
      // viaMat[T, Mat2, Mat3](flow: Graph[FlowShape[Out, T], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): Source[T, Mat3]
      val done: Future[Done] = Source(1 to 10).viaMat(doubleFlow)(Keep.right).runForeach(justPrint)
      // Keep.right is not really used here
      val done2: Future[Done] =
        Source(1 to 10).viaMat(doubleFlow)((a: NotUsed, b: NotUsed) => Unit).runForeach(justPrint)
      Await.result(done2, 1 second)
    }

    // to(sink) = toMat(sink)(Keep.both)

  }

}
