package com.example.sargon

import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait StopSystemAfterAll extends BeforeAndAfterAll {
  this: TestKit with Suite =>

  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)
}