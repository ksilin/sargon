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

import akka.Done
import akka.actor.Status.Success
import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.pattern.pipe
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import org.scalatest.{ FreeSpecLike, MustMatchers }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

// http://doc.akka.io/docs/akka/current/scala/stream/stream-testkit.html#TestKit
//http://doc.akka.io/docs/akka/current/scala/stream/stream-integrations.html#integrating-with-actors

class StreamToActorIntegrationSpec(_system: ActorSystem)
    extends TestKit(_system)
    with FreeSpecLike
    with MustMatchers
    with ImplicitSender {

  def this() = this(ActorSystem("streamToActorIntegrationSpec"))
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  "exchanging data between actors and streams" - {

    "piping to a sink actor" in {
      val sourceUnderTest = Source(1 to 4).grouped(2)

      val probe = TestProbe()

      sourceUnderTest.grouped(2).runWith(Sink.head).pipeTo(probe.ref)
      probe.expectMsg(100.millis, Seq(Seq(1, 2), Seq(3, 4)))
    }

    // mapMaterializedValue
    "creating an actor receiving msgs to stream" in {

      // there is also the more complex way of creating an actor source - implement ActorProducer with own actor

      // Messages sent to the actor that is materialized by Source.actorRef will be emitted to the stream
      // if there is demand from downstream, otherwise they will be buffered until request for demand is received.
      val source: Source[Int, ActorRef] = Source.actorRef[Int](0, OverflowStrategy.fail)

      // in case you have some futures providing individual values, you can combine them in a method:
      def run(actor: ActorRef): Unit = {
        import system.dispatcher
        Future { Thread.sleep(100); actor ! 1 }
        Future { Thread.sleep(200); actor ! 2 }
        Future { Thread.sleep(300); actor ! 3 }
      }

      val done: Future[Done] = source
        .mapMaterializedValue(sourceRef => {
//          run(sourceRef)
          system.scheduler.schedule(0 millis, 100 millis, sourceRef, 1)
          // sending Success to successfully complete the stream - does not work - it is accepted as a simple msg
          system.scheduler.scheduleOnce(500 millis, sourceRef, Success)

          // a PoisonPill works as Success however
          system.scheduler.scheduleOnce(750 millis, sourceRef, PoisonPill)
          // dispatching a msg directly in mapMatVal result in element drop
          // Dropping element because there is no downstream demand: [99]
          sourceRef ! 99
        })
        .runForeach({ e: Any =>
          println(s"done $e")
        })
      Await.result(done, 1 second)
    }
  }
}
