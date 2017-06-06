package com.example.sargon.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.example.sargon.Timed
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FreeSpec, MustMatchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class MapAsyncThreadSpec extends FreeSpec with MustMatchers with Timed with LazyLogging {

  implicit val sys = ActorSystem("asyncThreadSpec")
  implicit val mat = ActorMaterializer()

  val getF: Int => Future[String] = (i: Int) => {
    Future {
      logger.info(s" getF: $i : ${Thread.currentThread().getName}")
      Thread.sleep(500)
      i.toString
    }
  }

  val proc: String => Int = s => {
    logger.info(s"proc $s : ${Thread.currentThread().getName}")
    Thread.sleep(100)
    s.length
  }

  val waitF: Int => Future[String] = i => {
    logger.info(s"desyncF $i : ${Thread.currentThread().getName}")
    Future.successful(Await.result(getF(i), 10.seconds))
  }

  "which threads do asyncUnordered runs upon - futures" in {

    val para = 4
    val vals = (1 to 8).toList

    val source = Source(vals).mapAsyncUnordered(para) { i =>
      getF(i)
    }

    timed(s"await async $para") {
      val done = source.runWith(Sink.ignore)
      Await.result(done, 10.seconds)
    }
  }
  "which threads do asyncUnordered runs upon - awaited futures" in {
    val para = 4
    val vals = (1 to 8).toList

    val source2 = Source(vals).mapAsyncUnordered(para) { i =>
      waitF(i)
    }

    timed(s"await sync $para") {
      val done = source2.runWith(Sink.ignore)
      Await.result(done, 10.seconds)
    }

  }

}
