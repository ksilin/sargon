package com.example.sargon.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{FreeSpec, MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class FlatMapConcatMergeSpec extends FreeSpec with MustMatchers {

  implicit val system = ActorSystem("flatMapConcatMergeSpec")
  implicit val mat    = ActorMaterializer()


  // with FlatMap - each element is transformed into a source. these are then combined into a common source

  val baseSrc = Source(List(1, 2, 3))

  "flatMapConcat must fully consume one source before starting with the next" in {
    val run = baseSrc.flatMapConcat( n => Source(n*100 to n*100 + 99) ).runWith(Sink.foreach(println))
    Await.result(run, 3.seconds)
  }

  // order within each source remains, no order between sources
  "flatMapMerge must consumes all sources when elements become available" in {
    val run = baseSrc.flatMapMerge( 3, n => Source(n*100 to n*100 + 99) ).runWith(Sink.foreach(println))
    Await.result(run, 3.seconds)
  }

}