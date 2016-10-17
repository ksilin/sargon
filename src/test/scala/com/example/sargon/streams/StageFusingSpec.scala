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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Balance, Flow, GraphDSL, Merge, Source }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Attributes, FlowShape }
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.concurrent.Future

class StageFusingSpec extends FreeSpec with MustMatchers with LazyLogging {

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  import system.dispatcher // ec

  private val matSettings: ActorMaterializerSettings =
    ActorMaterializerSettings(system).withDebugLogging(true).withFuzzing(true)

  // by default, stages (.map, .via etc) are attempted to be fused - put into a single actor for performance purposes.

//  https://github.com/akka/akka/issues/20017

  def asyncBalancerAsyncWorker[In, Out](worker: Flow[In, Out, NotUsed], workerCount: Int): Flow[In, Out, NotUsed] = {
    import GraphDSL.Implicits._

    Flow.fromGraph(GraphDSL.create() { implicit b =>
      val balancer = b.add(Balance[In](workerCount))
      val merge    = b.add(Merge[Out](workerCount))

      for (_ <- 1 to workerCount) {
        balancer ~> worker.async ~> merge // async worker
      }
      FlowShape(balancer.in, merge.out)
    })
  }

  val workFun: (Int) => Int = e => {
    println("start w1 " + e)
    Thread.sleep(500)
    e
  }

  val workFutureFun: (Int) => Future[Int] = e => {
    Future {
      println("start wf " + e)
      Thread.sleep(e * 100)
      e
    }
  }

  val workFutureFutureFun: (Future[Int]) => Future[Int] = e => {
    e.flatMap { ev =>
      Future {
        println(s"start stage 2 $ev")
        Thread.sleep(ev * 100)
        ev
      }
    }
  }

  val asyncUnbufferedFlow = Flow[Int].map(workFun).async.addAttributes(Attributes.inputBuffer(1, 1))
  val asyncBufferedFlow   = Flow[Int].map(workFun).async

  val syncUnbufferedFlow = Flow[Int].map(workFun).addAttributes(Attributes.inputBuffer(1, 1))
  val syncBufferedFlow   = Flow[Int].map(workFun)

  "operator fusion and async mapping" - {

    // fusing or not, seems to have no effect
    implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(false))

    "map" in {
      Source((1 to 10).toList).map(workFun).runForeach(e => { println(s"map done $e") }) // 2
      Thread.sleep(1000)
    }

    "map async" in {
      Source((1 to 10).toList).mapAsync(4)(workFutureFun).runForeach(e => { println(s"map async done $e") }) // 7
      Thread.sleep(1000)
    }

    // TODO - its unordered alright, but shouldnt it be faster? perhaps not with the deterministic load
    "map async unordered" in {
      Source((1 to 10).toList)
        .mapAsyncUnordered(4)(workFutureFun)
        .runForeach(e => { println(s"unordered done $e") }) // 7
      Thread.sleep(1000)
    }
  }

  "operator fusion and async flows" - {
    implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(true))

    // Im not seeing any difference

    "async unbuffered" in {
      Source((1 to 10).toList).via(asyncUnbufferedFlow).runForeach(e => { println(s"unbuffered done $e") })
      Thread.sleep(1000)
    }

    "async buffered" in {
      Source((1 to 10).toList).via(asyncBufferedFlow).runForeach(e => { println(s"buffered done $e") })
      Thread.sleep(1000)
    }

  }

  "operator fusion and parallel workers" - {

    "with fusing" in {
      implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(true))

      Source((1 to 10).toList)
        .via(asyncBalancerAsyncWorker(asyncUnbufferedFlow, 5))
        .runForeach(e => { println(s"fused done $e") })

      Thread.sleep(1000)
    }

    "without fusing" in {
      implicit val unfusingMat = ActorMaterializer(matSettings.withAutoFusing(false))

      Source((1 to 10).toList)
        .via(asyncBalancerAsyncWorker(asyncUnbufferedFlow, 5))
        .runForeach(e => { println(s"unfused done $e") })

      Thread.sleep(1000)
    }
  }

}
