import sbt._

object Version {
  final val Scala     = "2.11.8"
  final val Akka = "2.4.8"

  final val ScalaLogging = "3.4.0"
  final val Logback = "1.1.3"
  final val ScalaTest = "3.0.0-RC4"
}

object Library {

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.Akka
  val akkaAgent = "com.typesafe.akka" %% "akka-agent" % Version.Akka
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.Akka
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % Version.ScalaLogging
  val logback = "ch.qos.logback" % "logback-classic" % Version.Logback

  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % Version.Akka
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.Akka
  val scalaTest = "org.scalatest" %% "scalatest" % Version.ScalaTest
}
