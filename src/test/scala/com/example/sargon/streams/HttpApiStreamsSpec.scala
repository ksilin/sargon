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

package com.example.sargon.streams

import akka.NotUsed
import akka.event.Logging
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.concurrent.Future

// apprentice piece - do comparative actor impl
class HttpApiStreamsSpec extends FreeSpec with ScalatestRouteTest with MustMatchers with LazyLogging {

  // RequestContext ⇒ Future[RouteResult]
  val clientRouteLogged = DebuggingDirectives.logRequestResult("stream api", Logging.InfoLevel)

  val route: Route = clientRouteLogged {
    get {
      path("etyFromSource" / Segment) { req =>
        complete {
          val dataSource: Source[String, NotUsed] = processRequestStringSource(req)
          val e: Chunked =
            HttpEntity.Chunked.fromData(ContentTypes.`text/plain(UTF-8)`, dataSource.map(ByteString.apply))
          HttpResponse(entity = e)
        }
      } ~
        path("stringFuture" / Segment) { req =>
          complete { processRequestString(req) }
        } ~
        path("concatFuture" / Segment) { req =>
          complete { processRequestStringConcat(req) }
        } ~ path("dandy") {complete(FineAndDandy.toString)} // needs marshaller

    }
  }

  val fetch: String => Future[String] = (req: String) => {
    println(s"starting work on $req")
    Thread.sleep(10)
    Future {
      Thread.sleep(100)
      println(s"work on $req finished, returning")
      s"$req is done"
    }
  }

  def concatFutures[T](source: Source[Future[T], NotUsed]): Source[T, NotUsed] = {
    source.flatMapConcat(Source.fromFuture)
  }

  val toResult: Future[String] => Future[Result] = (fs: Future[String]) => { fs map (_ => FineAndDandy) }

  // TODO - what if we receive multiple results per query string?

  val resultToString: Result => String = (r: Result) => { r.toString }

  def processRequestString(request: String): Future[String] = {
    val preRun: Source[Future[Result], NotUsed] = Source.single(request).map(fetch).map(toResult)
    val flatSource: Source[String, NotUsed]     = concatFutures(preRun).map(resultToString)
    flatSource.runWith(Sink.head) // expecting single result
  }

  def processRequestStringConcat(request: String): Future[String] = {
    val preRun: Source[Future[Result], NotUsed] = Source.single(request).map(fetch).map(toResult)
    val flatSource: Source[String, NotUsed]     = concatFutures(preRun).map(resultToString)
    flatSource.runWith(Sink.fold("")(_ + _)) // expecting multiple results and simply glueing them together
  }

  def processRequestStringSource(request: String): Source[String, NotUsed] = {
    val preRun: Source[Future[Result], NotUsed] = Source.single(request).map(fetch).map(toResult)
    val flatSource: Source[String, NotUsed]     = concatFutures(preRun).map(resultToString)
    flatSource
  }

  sealed trait Result
  final case object FineAndDandy extends Result
  final case object Apocalypse   extends Result
  case class Sx(s: String)

  "testing the route" - {

    "simple chunked get request" in {
      val req: String = "x"
      Get("/etyFromSource/" + req) ~> route ~> check {
        status must be(OK)
        responseAs[String] mustBe FineAndDandy.toString //s"$req is done"
      }
    }

    "simple concat get request" in {
      val req: String = "x"
      Get("/concatFuture/" + req) ~> route ~> check {
        status must be(OK)
        responseAs[String] mustBe FineAndDandy.toString //s"$req is done"
      }
    }

    "simple string get request" in {
      val req: String = "x"
      Get("/concatFuture/" + req) ~> route ~> check {
        status must be(OK)
        responseAs[String] mustBe FineAndDandy.toString //s"$req is done"
      }
    }
  }
}
