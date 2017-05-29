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
import akka.stream._
import akka.stream.scaladsl.{ Balance, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source }
import akka.{ Done, NotUsed }
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.Random

class WorkerPoolSpec extends FreeSpec with MustMatchers with LazyLogging {

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  implicit val mat    = ActorMaterializer((ActorMaterializerSettings(system).withDebugLogging(true).withFuzzing(true)))

  case class SimpleWorkerPoolShape[In, Out](inlet: Inlet[In], outlet: Outlet[Out]) extends Shape {

    override val inlets: immutable.Seq[Inlet[_]]   = inlet :: Nil
    override val outlets: immutable.Seq[Outlet[_]] = outlet :: Nil

    override def deepCopy() = SimpleWorkerPoolShape(inlet.carbonCopy(), outlet.carbonCopy())

    override def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]) = {
      assert(inlets.size == this.inlets.size)
      assert(outlets.size == this.outlets.size)
      // This is why order matters when overriding inlets and outlets.
      SimpleWorkerPoolShape[In, Out](inlets(0).as[In], outlets(0).as[Out])
    }
  }

  // TODO - for multiple inputs and single output use FanInShape
  // TODO - for multiple inputs and multiple outputs, use custom shape

  object PriorityWorkerPool {
    def apply[In, Out](worker: Flow[In, Out, Any], workerCount: Int): Graph[FlowShape[In, Out], NotUsed] = {

      GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val balance: UniformFanOutShape[In, In]       = b.add(Balance[In](workerCount))
        val resultsMerge: UniformFanInShape[Out, Out] = b.add(Merge[Out](workerCount))

        // workers are async
        for (i <- 0 until workerCount)
          balance.out(i) ~> worker.async ~> resultsMerge.in(i)

        // since we have a single in and a single out, we dont need a dedicated shape type
//        SimpleWorkerPoolShape(inlet = balance.in, outlet = resultsMerge.out)
        FlowShape(in = balance.in, out = resultsMerge.out)
      }
    }
  }

  // now, create a graph with the worker pool

  val worker1 = Flow[String].map { s =>
    Thread.sleep(Random.nextInt(100))
    s"w1 processed $s"
  }
  val worker2 = Flow[String].map { s =>
    Thread.sleep(Random.nextInt(100))
    s"w2 processed $s"
  }

  // TODO - what is this sorcery? how does the implicit work here to return Future[Done]
  val g: Graph[ClosedShape, Future[Done]] = GraphDSL.create(Sink.foreach(println)) { implicit b => sink =>
    import GraphDSL.Implicits._

    val pool1 = b.add(PriorityWorkerPool(worker1, 4))
    val pool2 = b.add(PriorityWorkerPool(worker2, 4))

    // TODO - source should be passed from outside
    Source(1 to 100).map("job: " + _) ~> pool1.in

    pool1.out ~> pool2.in

    // TODO - and now for some testing help:
    pool2.out ~> sink

    ClosedShape
//    SourceShape(pool2.out)
  }

  "using the worker pool" - {

    val run1: Future[Done] = RunnableGraph.fromGraph(g).run()
    val result             = Await.result(run1, 10 seconds)
    println(result)
  }

}
