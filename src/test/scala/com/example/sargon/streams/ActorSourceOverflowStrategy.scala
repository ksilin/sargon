package com.example.sargon.streams

import akka.Done
import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{ FreeSpecLike, MustMatchers }

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class ActorSourceOverflowStrategy(_system: ActorSystem)
    extends TestKit(_system)
    with FreeSpecLike
    with MustMatchers
    with ImplicitSender {

  def this() = this(ActorSystem("actorSourceOverflowStrategy"))
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  "unbufffered source without backpressure - actor drops new msgs if no demand" in {

    // expect a bunch of
    // akka.stream.impl.ActorRefSourceActor - Dropping element because there is no downstream demand: [tick]

    val source: Source[Double, ActorRef] =
      // will not fail of the buffer size is 0 = no buffer -> no overflow -> no failure
      Source.actorRef[String](0, OverflowStrategy.fail).map(_ => Random.nextDouble())

    val sink: Sink[Double, Future[Done]] = Sink.foreach { e =>
      println(s"start $e")
      Thread.sleep(100)
      println(s"done $e")
    }
    val actorRef: ActorRef = Flow[Double].to(sink).runWith(source)

    system.scheduler.schedule(0.micro, 1.milli, actorRef, "tick")
    system.scheduler.scheduleOnce(500.milli, actorRef, PoisonPill)
    Thread.sleep(1000)
    println("done")
  }

  "buffered source without backpressure fails on overflow" in {

    // expect
    // ERROR akka.stream.impl.ActorRefSourceActor - Failing because buffer is full and overflowStrategy is: [Fail]
    // DEBUG akka.stream.impl.ActorRefSourceActor - stopped

    val source: Source[Double, ActorRef] =
      Source.actorRef[String](1, OverflowStrategy.fail).map(_ => Random.nextDouble())

    var q = mutable.Queue[Double]()

    val sink: Sink[Double, Future[Done]] = Sink.foreach { e =>
      Thread.sleep(100)
      q += e
      println(e)
    }

    val actorRef: ActorRef = source.to(sink).run()
    system.scheduler.schedule(1.milli, 1.milli, actorRef, "tick")
    // to finish gracefully, send Success/PoisonPill to actor
    Thread.sleep(500)
    q.size mustBe <(5)
    println(s"done: ${q.size}")
  }

}
