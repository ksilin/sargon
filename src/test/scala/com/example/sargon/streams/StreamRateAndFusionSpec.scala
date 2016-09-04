package com.example.sargon.streams

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.Fusing.FusedGraph
import akka.stream._
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}
import com.example.sargon.Timed
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FreeSpec, MustMatchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class StreamRateAndFusionSpec extends FreeSpec with MustMatchers with LazyLogging with Timed {

  implicit val system = ActorSystem(this.getClass.getSimpleName)

  private val matSettings: ActorMaterializerSettings =
    ActorMaterializerSettings(system).withDebugLogging(true).withFuzzing(true)

  // confirming findings: https://github.com/akka/akka/issues/20017
  // more explanation - see http://doc.akka.io/docs/akka/current/scala/stream/stream-rate.html

  "stream rate" - {

    val source = Source(1 to 10)
      .map { i => print(s"A"); i }.async//.withAttributes(Attributes.inputBuffer(1,1)) - if buffering is to be adapted per stage
      .map { i => print(s"B"); i }.async//.withAttributes(Attributes.inputBuffer(1,1))
      .map { i => print(s"C"); i }.async//.withAttributes(Attributes.inputBuffer(1,1))


    // TODO - ok, now I have the fused graphs with a source shape. How do I run them? Less than obvious
    val fusedSource: FusedGraph[SourceShape[Int], NotUsed] = Fusing.aggressive(source)

    // fusing -> ABC ABC
    // without fusing -> ACBBAC
    "fusing unbuffered" in {
      implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(true).withInputBuffer(1, 1))

      val eventualDone: Future[Done] = source.runWith(Sink.ignore)
      Await.result(eventualDone, 10 seconds)
      // ABCABCABCABCABCABCABCABCABCABC
    }

    "not fusing unbuffered" in {
      implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(false).withInputBuffer(1, 1))

      val eventualDone: Future[Done] = source.runWith(Sink.ignore)
      Await.result(eventualDone, 10 seconds)
      // ABCBACBACBACBCABABACCBACABCBC
    }

    "fusing buffered" in {
      implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(true).withInputBuffer(16, 16))

      val eventualDone: Future[Done] = source.runWith(Sink.ignore)
      Await.result(eventualDone, 10 seconds)
      // ABCABCABCABCABCABCABCABCABCABC
    }

    "not fusing buffered" in {
      implicit val fusingMat = ActorMaterializer(matSettings.withAutoFusing(false).withInputBuffer(16, 16))

      val eventualDone: Future[Done] = source.runWith(Sink.ignore)
      Await.result(eventualDone, 10 seconds)
      // AABABABBCCCCAAAAABACBCBCBCBCBC
      // AAAABABABABABABBCABBCBCCCCCCCC
      // TODO - buffering seems to induce more parallelism or simply more stalling - why - ask @ SO?
      // what would happen with longer-running jobs?
    }

  }

}
