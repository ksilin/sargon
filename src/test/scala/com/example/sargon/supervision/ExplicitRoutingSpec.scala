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

import akka.actor.Actor.Receive
import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Terminated }
import akka.event.{ Logging, LoggingReceive }
import akka.routing.{ ActorRefRoutee, RoundRobinRoutingLogic, Router }
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.example.sargon.supervision.Worker._
import org.scalatest.{ FreeSpecLike, MustMatchers }

import scala.concurrent.duration._
import scala.util.Random

class ExplicitRoutingSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with MustMatchers
    with FreeSpecLike {

  def this() = this(ActorSystem("SupervisionSpec"))
  system.eventStream.setLogLevel(Logging.DebugLevel)

  implicit val timeout = Timeout(10 seconds)

  class RoutingSuperExplicit(workerProps: Props) extends Actor with ActorLogging {

    // this router is not an actor!
    var router: Router = {
      val routees = Vector.fill(5) {
        val r = context.actorOf(workerProps, s"worker-${Random.alphanumeric.take(3).mkString}")
        context watch r // death watch
        ActorRefRoutee(r)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }

    override def receive: Receive = LoggingReceive {
      case Terminated(a) =>
        router = router.removeRoutee(a)
        val r = context.actorOf(workerProps, s"worker-${Random.alphanumeric.take(3).mkString}")
        context watch r
        router = router.addRoutee(r)
      case w: Any => // let the workers sort everything out
        router.route(w, sender())
    }

    override val supervisorStrategy = loggingRestartOneForOneStrategy(log)
  }

  "test routing and failure-resistance" - {

    "on worker failure, the entire router is restarted" in {

      val routerProps: Props = Props(new RoutingSuperExplicit(Props[Worker2]))
      val r                  = system.actorOf(routerProps, "explicitRouter")
      r ! Fail
      // no policy defined - actor will restart until timeout
      expectNoMsg(100 millis)
    }

    // TODO - "how to log that the maxNumOfRetries has been depleted"

  }

}
