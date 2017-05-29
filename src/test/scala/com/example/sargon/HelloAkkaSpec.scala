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

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import org.scalatest.{ BeforeAndAfterAll, FreeSpecLike, Matchers }

import scala.concurrent.duration._

class HelloAkkaSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with Matchers
    with FreeSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("HelloAkkaSpec"))

  override def afterAll: Unit = {
    system.terminate()
    //    system.awaitTermination(10.seconds)
  }

  "some easy tests" - {

    "An HelloAkkaActor should be able to set a new greeting" in {
      val greeter = TestActorRef(Props[Greeter])
      greeter ! WhoToGreet("testkit")
      greeter.underlyingActor.asInstanceOf[Greeter].greeting should be(
        "hello, testkit"
      )
    }

    "should be be able to get a new greeting" in {
      val greeter = system.actorOf(Props[Greeter], "greeter")
      greeter ! WhoToGreet("testkit")
      greeter ! Greet
      expectMsgType[Greeting].message.toString should be("hello, testkit")
    }
  }

}
