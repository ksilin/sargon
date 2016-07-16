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

package com.example.sargon

import akka.actor.ActorSystem
import akka.agent.Agent
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class AgentSpec(_system: ActorSystem)
  extends TestKit(_system)
      with ImplicitSender
      with Matchers
      with FreeSpecLike
      with BeforeAndAfterAll {

//  http://doc.akka.io/docs/akka/current/scala/agents.html

    def this() = this(ActorSystem("AgentSpec"))

    override def afterAll: Unit = {
      system.terminate()
      //    system.awaitTermination(10.seconds)
    }

    "what is an agent and what is it for" - {
      import system.dispatcher

      // Reading an Agent's current value does not involve any message passing and happens immediately.
      // while updates to an Agent are asynchronous, reading the state of an Agent is synchronous.

      "An HelloAkkaActor should be able to set a new greeting" in {

        val initialValue: Int = 5
        val agent = Agent(initialValue)
        val result = agent()
        result should be(initialValue)


        // send does not return anything
        val unit: Unit = agent send 7 // change to the value is enqueued
        agent send( _ + 3)
        agent() shouldNot be(10) // changes are not visible immediately
        Thread.sleep(100)
        // for longer-running ops, use sendOff - it will use its own thread

        // Dispatches using either sendOff or send will still be executed in order.
        agent() should be(10)

        // send update and get future of changed value
        val f12 = agent alter 12

        Await.result(f12, 1 second) should be(12)

        // you can also get a Future of the agents val with
        Await.result(agent.future, 1 second) should be(12)
      }

      "agents are monadic" in {

        val agent1 = Agent(3)
        val agent2 = Agent(5)

        // In mondic usage, new agents are created, leaving original agents untouched

        // uses foreach
        for (value <- agent1)
          println(value)

        // uses map
        val agent3: Agent[Int] = for (value <- agent1) yield value + 1

        agent3.get() should be(4)

        // or using map directly
        val agent4: Agent[Int] = agent1 map (_ + 1)
        agent4.get() should be(5)

        // uses flatMap
        val agent5: Agent[Int] = for {
          value1 <- agent1
          value2 <- agent2
        } yield value1 + value2
        agent5.get() should be(10)
      }
    }
  }
