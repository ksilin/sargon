package com.example.sargon.streams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Merge, Sink, Source, Zip, ZipN, ZipWith2, ZipWithN }
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.collection.immutable
import scala.collection.parallel.immutable
import scala.concurrent.Await
import scala.concurrent.duration._

class CombineSourcesSpec extends FreeSpec with MustMatchers {

  implicit val system = ActorSystem("combineSourcesSpec")
  implicit val mat    = ActorMaterializer()

  "must combine two simple sources" in {

    val s1: Source[Int, NotUsed] = Source((1 to 100).toList)
    val s2: Source[Int, NotUsed] = Source((201 to 300).toList)

    val combo: Source[Int, NotUsed] = Source.combine(s1, s2)(Merge(_))

    val run = combo.runWith(Sink.foreach(println))
    Await.result(run, 10.seconds)
  }

  "must zip sources with combine/ZipWithN" in {

    val s1: Source[Int, NotUsed] = Source((1 to 100).toList)
    val s2: Source[Int, NotUsed] = Source((201 to 300).toList)

    val zipped: Source[(Int, Int), NotUsed] = Source.combine(s1, s2)(x => {
      new ZipWithN[Int, (Int, Int)]((is: Seq[Int]) => (is.head, is.tail.head))(x)
    })
    val run = zipped.runWith(Sink.foreach(println))
    Await.result(run, 10.seconds)
  }

  "must zip sources with zipN" in {

    val s1: Source[Int, NotUsed] = Source((1 to 100).toList)
    val s2: Source[Int, NotUsed] = Source((201 to 300).toList)

    // three is no zip2 and the output is always a Seq
    val zip2: Source[Seq[Int], NotUsed] = Source.zipN[Int](List(s1, s2))
    val run                             = zip2.runWith(Sink.foreach(println))
    Await.result(run, 10.seconds)
  }

  "must zip sources with zipWithN" in {

    val s1: Source[Int, NotUsed] = Source((1 to 100).toList)
    val s2: Source[Int, NotUsed] = Source((201 to 300).toList)

    // zipWith gives you more freedom
    val zip2 = Source.zipWithN[Int, (Int, Int)] { ints: Seq[Int] =>
      (ints.head, ints.tail.head)
    }(List(s1, s2))
    val run = zip2.runWith(Sink.foreach(println))
    Await.result(run, 10.seconds)
  }

  // shorter source determines the combined stream length

}
