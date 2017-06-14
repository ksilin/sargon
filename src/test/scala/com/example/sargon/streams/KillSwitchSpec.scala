package com.example.sargon.streams

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, KillSwitches, UniqueKillSwitch }
import akka.stream.scaladsl.{ Keep, RunnableGraph, Sink, Source }
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class KillSwitchSpec extends FreeSpec with MustMatchers {

  implicit val system = ActorSystem("killswitchspec")
  implicit val mat    = ActorMaterializer()

  val continuousStream: RunnableGraph[(UniqueKillSwitch, Future[Done])] =
    Source
      .tick(0.milli, 1.milli, "tick")
      .map { s =>
        println(s)
        s.toUpperCase()
      }
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach(println))(Keep.both)

  "must stop stream with kill switch & mat" in {

    val (killSwitch, doneF): (UniqueKillSwitch, Future[Done]) =
      continuousStream.run()

    killSwitch.shutdown()
    val done = Await.result(doneF, 10.seconds)
    done mustBe Done
  }

  "must fail with Exception on killSwitch.abort" in {
    val (killSwitch, doneF): (UniqueKillSwitch, Future[Done]) =
      continuousStream.run()

    val exception = new Exception("Exception from KillSwitch")
    killSwitch.abort(exception) // stream fails with exception
    val ex = intercept[Exception] { Await.result(doneF, 10.seconds) }
    ex mustBe exception

  }

}
