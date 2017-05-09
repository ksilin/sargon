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
import akka.stream.scaladsl.{ Balance, Flow, GraphDSL, Merge, Source }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Attributes, FlowShape }
import com.example.sargon.Timed
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class AsyncMultistageFusingSpec extends FreeSpec with MustMatchers with LazyLogging with Timed {

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  import system.dispatcher // ec

  private val matSettings: ActorMaterializerSettings =
    ActorMaterializerSettings(system).withDebugLogging(true).withFuzzing(true)

  val workFun: (Int) => Int = e => {
    println(s"start w1: $e")
    Thread.sleep(e * 100)
    e
  }

  val workFutureFun: (Int) => Future[Int] = e => {
    Future {
      println(s"start wf $e" )
      Thread.sleep(e * 100)
      e
    }
  }

  val workFutureFutureFun: (Future[Int]) => Future[Int] = e => {
    e.flatMap { ev =>
      Future {
        println(s"start wff $ev")
        Thread.sleep(ev * 100)
        ev
      }
    }
  }

  // TODO - for async without parallelism, there is also .map().async

  private val numbers: List[Int] = (1 to 10).toList
  "operator fusion and async mapping" - {

    // fusing or not, seems to have no effect
    implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(true)) // switch fusing here

    "map" in {
      timed("map") {
        val done: Future[Done] = Source(numbers).map(workFun).runForeach(e => { println(s"done $e") })
        Await.result(done, 10.seconds)
      }
    } // 5564 ms

    "map async" in {
      timed("map async") {
        val done: Future[Done] = Source(numbers)
          .mapAsync(4)(workFutureFun)
          .runForeach(e => {
            println(s"done $e")
          })
        Await.result(done, 10.seconds)
      } // 1857 ms
    }

    // TODO - its unordered alright, but shouldnt it be faster? perhaps not with the deterministic load
    "map async unordered" in {
      timed("map async unordered") {
        val done: Future[Done] = Source(numbers)
          .mapAsyncUnordered(4)(workFutureFun)
          .runForeach(e => {
            println(s"done $e")
          })
        Await.result(done, 10 seconds)
      } // 1820 ms
    }

    "map 2 stages" in {
      timed("map 2 stages") {
        val done: Future[Done] = Source(numbers).map(workFun).map(workFun).runForeach(e => { println(s"done $e") }) // 1
        Await.result(done, 10 seconds)
      }
    } // 6568 ms // interesting, the stages seem to run in parallel without fusing
    // times out when fusing is switched on

    "map async 2 stages" in {
      timed("map async 2 stages") {
        val done: Future[Done] = Source(numbers)
          .mapAsync(4)(workFutureFun)
          .mapAsync(4)(workFutureFun)
          .runForeach(e => {
//          println(s"done $e")
          })
        Await.result(done, 10 seconds)
      } // 2844 ms - no fusing, 2870 ms fusing
    }

    // its unordered alright, but shouldnt it be faster? perhaps not with the deterministic load
    "map async unordered 2 stages" in {
      timed("map async unordered 2 stages") {
        val done: Future[Done] = Source(numbers)
          .mapAsyncUnordered(4)(workFutureFun)
          .mapAsyncUnordered(4)(workFutureFun)
          .runForeach(e => {
//          println(s"done $e")
          })
        Await.result(done, 10 seconds)
      } // 2874 ms no fusing // 2887 ms with fusing
    }
  }

  "operator fusion and async flows" - {

    val asyncUnbufferedFlow = Flow[Int].map(workFun).async.addAttributes(Attributes.inputBuffer(1, 1))
    val asyncBufferedFlow   = Flow[Int].map(workFun).async

    val syncUnbufferedFlow = Flow[Int].map(workFun).addAttributes(Attributes.inputBuffer(1, 1))
    val syncBufferedFlow   = Flow[Int].map(workFun)

    // no difference with or without fusing
    implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(false))

    "sync unbuffered" in {
      timed("sync unbuffered") {
        val done: Future[Done] = Source(numbers).via(syncUnbufferedFlow).runForeach(e => {}) // 1
        Await.result(done, 10 seconds)
      } // 5554
    }

    "sync buffered" in {
      timed("sync buffered") {
        val done: Future[Done] = Source(numbers).via(syncBufferedFlow).runForeach(e => {}) // 1
        Await.result(done, 10 seconds)
      } // 5505
    }

    "async unbuffered" in {
      timed("async unbuffered") {
        val done: Future[Done] = Source(numbers).via(asyncUnbufferedFlow).runForeach(e => {}) // 1
        Await.result(done, 10 seconds)
      } // 5521
    }

    "async buffered" in {
      timed("async buffered") {
        val done: Future[Done] = Source(numbers).via(asyncBufferedFlow).runForeach(e => {}) // 1
        Await.result(done, 10 seconds)
      }
    } // 5513
  }

  "operator fusion and async future flows" - {

    val asyncUnbufferedFlow = Flow[Int].map(workFutureFun).async.addAttributes(Attributes.inputBuffer(1, 1))
    val asyncBufferedFlow   = Flow[Int].map(workFutureFun).async.addAttributes(Attributes.inputBuffer(16, 16)) // default ?

    val syncUnbufferedFlow = Flow[Int].map(workFutureFun).addAttributes(Attributes.inputBuffer(1, 1))
    val syncBufferedFlow: Flow[Int, Future[Int], NotUsed] =
      Flow[Int].map(workFutureFun).addAttributes(Attributes.inputBuffer(16, 16))

    val asyncUnbufferedFutureFlow =
      Flow[Future[Int]].map(workFutureFutureFun).async.addAttributes(Attributes.inputBuffer(1, 1))
    val asyncBufferedFutureFlow =
      Flow[Future[Int]].map(workFutureFutureFun).async.addAttributes(Attributes.inputBuffer(16, 16)) // default ?

    val syncUnbufferedFutureFlow =
      Flow[Future[Int]].map(workFutureFutureFun).addAttributes(Attributes.inputBuffer(1, 1))
    val syncBufferedFutureFlow: Flow[Future[Int], Future[Int], NotUsed] =
      Flow[Future[Int]].map(workFutureFutureFun).addAttributes(Attributes.inputBuffer(16, 16))

    // dramatic differences between fusing and not fusing
    implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(false))

    def waitAndPrint: (Future[Int]) => Unit = { e: Future[Int] =>
      {
        val resE = Await.result(e, 10 seconds)
        println(s"done $resE")
      }
    }

    "sync unbuffered" in {
      timed("sync unbuffered") {
        val done: Future[Done] = Source(numbers).via(syncUnbufferedFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      }
    } // 5562 // 1042 - without fusing

    "sync buffered" in {
      timed("sync buffered") {
        val done: Future[Done] = Source(numbers).via(syncBufferedFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      }
    } // 5512 // 1026 - without fusing

    "async unbuffered" in {
      timed("async unbuffered") {
        val done: Future[Done] = Source(numbers).via(asyncUnbufferedFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      }
    } // 3012 // 1016 - without fusing

    "async buffered" in {
      timed("async buffered") {
        val done: Future[Done] = Source(numbers).via(asyncBufferedFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      } // 1012 // 1011 - without fusing
    }

    "sync unbuffered 2 stage" in {
      timed("sync unbuffered 2 stage") {
        val done: Future[Done] =
          Source(numbers).via(syncUnbufferedFlow).via(syncUnbufferedFutureFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      }
    } //  // 2056 - without fusing

    "sync buffered 2 stage" in {
      timed("sync buffered 2 stage") {
        val done: Future[Done] =
          Source(numbers).via(syncBufferedFlow).via(syncBufferedFutureFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      }
    } //  // 1026 - without fusing

    "async unbuffered 2 stage" in {
      timed("async unbuffered 2 stage") {
        val done: Future[Done] =
          Source(numbers).via(asyncUnbufferedFlow).via(asyncUnbufferedFutureFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      }
    } //  // 1016 - without fusing

    "async buffered 2 stage" in {
      timed("async buffered 2 stage") {
        val done: Future[Done] =
          Source(numbers).via(asyncBufferedFlow).via(asyncBufferedFutureFlow).runForeach(waitAndPrint)
        Await.result(done, 10 seconds)
      } //  // 1011 - without fusing
    }

  }
}
