package com.example.sargon.streams

import akka.{ Done, NotUsed }
import akka.actor.{ Actor, ActorRef, ActorSystem, Cancellable, PoisonPill, Props }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, ClosedShape, OverflowStrategy }
import akka.stream.scaladsl.{ Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith }
import com.example.sargon.Timed
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.collection.mutable
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.Random

class WeatherForwarder extends Actor {
  override def receive: Receive = {
    case x =>
      println(x)
      sender() ! 0.21
  }
}
object WeatherForwarder {
  def props: Props = Props[WeatherForwarder]
}

class BuffersAndTicksSpec extends FreeSpec with MustMatchers with LazyLogging with Timed {

  case object Tick

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  private val matSettings: ActorMaterializerSettings =
    ActorMaterializerSettings(system).withDebugLogging(true).withFuzzing(true)
  implicit val ec = system.dispatcher

  "buffers and ticks" - {

    val g = RunnableGraph.fromGraph(GraphDSL.create(Sink.foreach(println)) { implicit b => sink =>
      import GraphDSL.Implicits._

      // this is the asynchronous stage in this graph
      val zipper = b.add(ZipWith[Tick.type, Int, Int]((_, count) => count).async)

      Source.tick(initialDelay = 300.millisecond, interval = 300.millisecond, Tick) ~> zipper.in0

      Source
        .tick(initialDelay = 100.millisecond, interval = 100.millisecond, "message!")
        .conflateWithSeed(seed = (_) => 1)((count, _) => count + 1) ~> zipper.in1

      zipper.out ~> sink
      ClosedShape
    })

    "buffered" in {
      implicit val mat      = ActorMaterializer(matSettings.withInputBuffer(16, 16))
      val run: Future[Done] = g.run()
      Await.result(run, 10.seconds)

      // 11111111111111115111111117111111117 // 17, not 7
      // fetches a full buffer, then accumulates for the last value of 17
      // not getting it still, why is there a 5 at first?
    }

    "unbuffered" in {
      implicit val mat      = ActorMaterializer(matSettings.withInputBuffer(1, 1))
      val run: Future[Done] = g.run()
      Await.result(run, 10 seconds)
      // 111333333333333333333333333333333
      // inital ones are due to prefetching of the ZipWith stage
    }

    // TODO - also test OverflowStrategy: http://doc.akka.io/docs/akka/2.4.9/scala/stream/stream-rate.html#Buffers_in_Akka_Streams

    implicit val mat = ActorMaterializer(matSettings.withInputBuffer(1, 1))

    // producer too fast, cannot be slowed down
    "conflate" in {

      // http://doc.akka.io/docs/akka/2.4.9/scala/stream/stream-rate.html#Rate_transformation

      import Math._

      val statsFlow = Flow[Double]
      // .groupedWithin(100, 100.milli)
      // (seed: Out ⇒ S)(aggregate: (S, Out) ⇒ S)
        .conflateWithSeed { d: Double =>
          Seq(d)
        } { (elem, acc) =>
          println(s"adding element $elem to sequence $acc") // never gets printed
          elem :+ acc
        }
        .map { s: Seq[Double] =>
          Thread.sleep(300)
          println(s"current size: ${s.size}")
          val μ  = s.sum / s.size
          val se = s.map(x => pow(x - μ, 2))
          val σ  = sqrt(se.sum / se.size)
          (σ, μ, s.size)
        }

      val s: Source[Double, Cancellable] = Source.tick(1.milli, 1.milli, "tick").map(_ => Random.nextDouble())

      // TODO - why am I getting single element stats?
      // runWith vs foreach with function dont make a difference
      val eventualDone
        : Future[Done] = s.via(statsFlow).runWith(Sink.foreach(e => { Thread.sleep(10); println(e) })) //foreach(e => { Thread.sleep(10); println(e) })
      Await.result(eventualDone, 1.second)
    }

    // producer too slow
    "expand" in {
      val lastFlow = Flow[Double].expand(Iterator.continually(_))

      val s: Source[Double, Cancellable] = Source.tick(1.milli, 1.milli, "tick").map(_ => Random.nextDouble())

      // TODO - why am I getting a single result repeatedly?
      val eventualDone: Future[Done] = s.via(lastFlow).runForeach(e => { Thread.sleep(10); println(e) })
      Await.result(eventualDone, 1.second)
    }

    "expand 2 " in {
      val lastFlow = Flow[Double].expand(i => Iterator.from(0).map(i -> _))

      val s: Source[Double, Cancellable] = Source.tick(1 milli, 1 milli, "tick").map(_ => Random.nextDouble())

      // TODO - why am I getting a single result repeatedly?
      val eventualDone: Future[Done] = s.via(lastFlow).runForeach(e => { Thread.sleep(10); println(e) })
      Await.result(eventualDone, 1 second)
    }

    "nothing wrong with the tick source" in {
      val s: Source[Double, Cancellable] = Source.tick(1 milli, 1 milli, "tick").map(_ => Random.nextDouble())

      val done: Future[Done] = s.runForeach(println)
      Await.result(done, 1 second)
    }
  }

}
