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

    "piping to a sink actor ref" in {
      val sourceUnderTest = Source(1 to 4).grouped(2)

      // TestProve extends TestKit, has testActor
      val probe: TestProbe = TestProbe()

      sourceUnderTest.grouped(2).runWith(Sink.head).pipeTo(probe.ref)
      probe.expectMsg(100.millis, Seq(Seq(1, 2), Seq(3, 4)))
    }


    // TODO - Success does not stop stream gracefully

    // mapMaterializedValue
    "creating an actor receiving msgs to stream" in {

      // there is also the more complex way of creating an actor source - implement ActorProducer with own actor

      // Messages sent to the actor that is materialized by Source.actorRef will be emitted to the stream
      // if there is demand from downstream, otherwise they will be buffered until request for demand is received.
      val source: Source[Int, ActorRef] = Source.actorRef[Int](0, OverflowStrategy.fail)

      val done: Future[Done] = source
        .mapMaterializedValue((sourceRef: ActorRef) => {
          system.scheduler.schedule(0.millis, 100.millis, sourceRef, 1)

          // sending Success to successfully complete the stream - does not work - accepted as simple msg and printed
          system.scheduler.scheduleOnce(500.millis, sourceRef, Success)

          // a PoisonPill works as Success however
          system.scheduler.scheduleOnce(750.millis, sourceRef, PoisonPill)
          // dispatching a msg directly in mapMatVal result in element drop
          // Dropping element because there is no downstream demand: [99]
          sourceRef ! 99
        })
        .runForeach({ e: Any =>
          println(s"done $e")
        })
      Await.result(done, 1.second)
    }

    "mapping the materialized value 2" in {
      val source: Source[Int, ActorRef] = Source.actorRef[Int](0, OverflowStrategy.fail)

      // in case you have some futures providing individual values, you can combine them in a method:
      def run(actor: ActorRef): Future[Unit] = {
        Future { Thread.sleep(100); actor ! 1 }
        Future { Thread.sleep(200); actor ! 2 }
        Future { Thread.sleep(300); actor ! 3 }
        // akka.stream.impl.ActorRefSourceActor - received AutoReceiveMessage
        // Envelope(PoisonPill,Actor[akka://streamToActorIntegrationSpec/system/testActor-1#1008056392])
        Future { Thread.sleep(400); actor ! PoisonPill }
      }

      val done: Future[Done] = source
        .mapMaterializedValue((sourceRef: ActorRef) => {
          run(sourceRef)
        })
        .runForeach({ e: Any =>
          println(s"done $e")
        })
      Await.result(done, 1.second)
    }

    // TODO - how fast can I process with a buffer and a fast parallel consumer

  }
}
