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

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.event.{ Logging, LoggingReceive }
import akka.routing.RoundRobinPool
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.example.sargon.supervision.Worker._
import org.scalatest.{ FreeSpecLike, MustMatchers }

import scala.concurrent.duration._

// http://doc.akka.io/docs/akka/current/scala/routing.html

class RoutingPoolSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with MustMatchers
    with FreeSpecLike {

  def this() = this(ActorSystem("SupervisionSpec"))
  system.eventStream.setLogLevel(Logging.DebugLevel)

  implicit val timeout = Timeout(10 seconds)

  class RoutingSuper(workerProps: Props) extends Actor with ActorLogging {

    // here, the router is a self-contained actor
    // The router creates routees as child actors and removes them from the router if they terminate
    // since the router is an actor, it can have its own supervisorStrategy
    // it is the property of the pool
    // the default strategy is escalate
    val router: ActorRef = // sincle we are handling props here, there is no way to name actors explicitly
    context.actorOf(RoundRobinPool(5).props(workerProps), "router")

    override def receive: Receive = LoggingReceive {
      case w: Any => router ! w
    }

    override val supervisorStrategy = loggingRestartOneForOneStrategy(log)
  }

  class RoutingSuperWithRestart(workerProps: Props) extends Actor with ActorLogging {

    // since the router is an actor, it can have its own supervisorStrategy
    // it is the property of the pool
    // the default strategy is escalate
    val router: ActorRef = // sincle we are handling props here, there is no way to name actors explicitly
    context.actorOf(RoundRobinPool(5, supervisorStrategy = loggingRestartOneForOneStrategy(log)).props(workerProps),
                    "router")

    override def receive: Receive = LoggingReceive {
      case w: Any => router ! w
    }
  }

  "test routing and failure-resistance" - {

    "on worker failure, the entire router is restarted" in {

      val routerProps: Props = Props(new RoutingSuper(Props[Worker2]))
      val r                  = system.actorOf(routerProps, "actorRouter")
      r ! Fail
      // router will restart, restarting all children, failing msg will be redispatched
      expectNoMsg(100 millis)
    }

    "on worker failure with appropriate strategy in router, only the worker is restarted" in {

      val routerProps: Props = Props(new RoutingSuperWithRestart(Props[Worker2]))
      val r                  = system.actorOf(routerProps, "actorRouter")
      r ! Fail
      // no policy defined - actor will restart until timeout
      expectNoMsg(100 millis)
    }

    // TODO - "removal of actor after termination - pool size check - are they re"

    // TODO - If the child of a pool router terminates, the pool router will not automatically spawn a new child.
    // In the event that all children of a pool router have terminated the router will terminate itself unless it is a dynamic router, e.g. using a resizer

    // TODO - PoisonPill sent to router will not propagate to workers. teh router will be stopped and stop its children
    // TODO - GetRoutees, Add/RemoveRoutee, AdjustPoolSize

    // TODO -resizable pool
  }

}
