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

package com.example.sargon.supervision

import akka.actor.{ ActorRef, ActorSystem, PoisonPill, Props, Terminated }
import akka.event.Logging
import akka.pattern.ask
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.example.sargon.supervision.SimpleWorkerSupervisor.GetWorker
import com.example.sargon.supervision.Worker._
import org.scalatest.{ FreeSpecLike, MustMatchers }
import pprint._

import scala.concurrent.Await
import scala.concurrent.duration._

class SupervisionSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with MustMatchers
    with FreeSpecLike {

  def this() = this(ActorSystem("SupervisionSpec"))
  system.eventStream.setLogLevel(Logging.DebugLevel)

  implicit val timeout = Timeout(10 seconds)

  "testing supervision" - {

    // Supervision related parent-child communication happens by special system messages that have their own mailboxes
    // separate from user messages.
    // This implies that supervision related events are not deterministically ordered relative to ordinary messages

    val stratSuperProps: Props  = Props(new StrategicWorkerSupervisor(Props[Worker]))
    val stratSuperProps2: Props = Props(new StrategicWorkerSupervisor(Props[Worker2]))

    "failing an restarting" in {

      val simpleSuperProps: Props = Props(new SimpleWorkerSupervisor(Props[Worker]))
      val master                  = system.actorOf(simpleSuperProps, "supervisor")

      val getWorker = (master ? GetWorker).mapTo[ActorRef]
      val w         = Await.result(getWorker, 3 seconds)
      watch(w)

      master ! Fail

      // DEBUG c.e.s.s.StrategicWorkerSupervisor - restarting
      // DEBUG c.example.sargon.supervision.Worker - stopped
      // DEBUG c.e.s.s.StrategicWorkerSupervisor - restarted
      // DEBUG c.example.sargon.supervision.Worker - started (com.example.sargon.supervision.Worker@1b18d48e)
      // DEBUG c.e.s.s.StrategicWorkerSupervisor - now supervising Actor[akka://HttpPusherSupervisorSpec/user/supervisor/worker-s9G

      // not getting the Terminated msg here because the actor is restarting and is not fully terminated
      expectNoMsg
    }

    "which msg killed the actor again?" in {

      val moribund = system.actorOf((Props(new Worker2)), "worker")
      moribund ! Fail

      // will keep restarting until the timout of expectNoMsg kicks in  - no maxNrOfRetries is defined
      // http://stackoverflow.com/questions/13542921/akka-resending-the-breaking-message
      expectNoMsg(1 second)
//      val termMsg: Terminated = expectMsgType[Terminated]//(1 second)
    }

    "supervisor escalating and being restarted" in {
      val master = system.actorOf(stratSuperProps2, "stratSuper")
      master ! EscalateThis

      val getWorker = (master ? GetWorker).mapTo[ActorRef]
      val w         = Await.result(getWorker, 3 seconds)
      watch(w)

      // since the StrategicWorkerSupervisor escalates, it will be restarted and restarts its children.
      // The EscalateMeEx will not be processed in the worker onRestart

      // however you will get a Terminated msg when on death watch
      val termMsg: Terminated = expectMsgType[Terminated](3 second)
    }

    "restarting worker" in {
      val master = system.actorOf(stratSuperProps2, "stratSuperRestart")
      master ! RestartMe

      val getWorker = (master ? GetWorker).mapTo[ActorRef]
      val w         = Await.result(getWorker, 3 seconds)
      watch(w)
      // will keep restarting on same msg until the timout of expectNoMsg kicks in or maxNrOfRetries is reached
      // http://stackoverflow.com/questions/13542921/akka-resending-the-breaking-message
      val termMsg: Terminated = expectMsgType[Terminated] //(1 second)
    }

    "resuming worker" in {
      val master = system.actorOf(stratSuperProps2, "stratSuperResume")

      val getWorker = (master ? GetWorker).mapTo[ActorRef]
      val w         = Await.result(getWorker, 3 seconds)
      watch(w)

      master ! ResumeMe
      expectNoMsg(1 second)
    }

    "stopping worker will not produce a Terminated msg" in {
      val master = system.actorOf(stratSuperProps2, "stratSuperStop")

      val getWorker = (master ? GetWorker).mapTo[ActorRef]
      val w         = Await.result(getWorker, 3 seconds)
      watch(w)

      master ! StopMe
      val termMsg: Terminated = expectMsgType[Terminated]
    }

    "stopping worker explicitly produce a Terminated msg" in {
      val master = system.actorOf(stratSuperProps2, "stratSuperHammertime")

      val getWorker = (master ? GetWorker).mapTo[ActorRef]
      val w         = Await.result(getWorker, 3 seconds)
      watch(w)

      master ! Hammertime
      val termMsg: Terminated = expectMsgType[Terminated]
    }

    "death watch - stopping worker with a PoisonPill" in {

      val moribund = system.actorOf((Props[Worker]), "workerPoison")
      watch(moribund)
      moribund ! PoisonPill // kill it for good so it does not restart

      val termMsg: Terminated = expectMsgType[Terminated](10 seconds)
      pprintln(termMsg)
      pprintln(termMsg.actor)
      pprintln(termMsg.addressTerminated)
      pprintln(termMsg.existenceConfirmed)
    }

    // TODO - parent failing - what happens to children? the default behavior of the preRestart hook of the Actor class is to terminate all its children before restarting

    // TODO - internal state on restart & resume

  }
}
