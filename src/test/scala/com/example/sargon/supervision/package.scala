package com.example.sargon

import akka.actor.{ Actor, ActorLogging, OneForOneStrategy, Props }
import akka.actor.SupervisorStrategy.{ Escalate, Restart, Resume, Stop }
import akka.event.{ LoggingAdapter, LoggingReceive }
import com.example.sargon.supervision.SimpleWorkerSupervisor.GetWorker

import scala.concurrent.duration._
import scala.util.Random

package object supervision {

  object Worker {
    case object Fail
    case object EscalateThis
    case object RestartMe
    case object ResumeMe
    case object StopMe
    case object Hammertime
    case object GetCount
  }

  class Worker extends Actor with ActorLogging {
    import Worker._

    var receivedMsg = 0

    def logAndCount(msg: Any): Unit = {
      log.info(s"${ self.path.name } received Fail")
      receivedMsg += 1
    }

    override def receive: Receive = LoggingReceive {
      case Fail =>
        logAndCount(Fail)
        throw new Exception(s"bam! in ${ self.path.name }")
      case EscalateThis =>
        logAndCount(EscalateThis)
        throw new EscalateThisEx(s"escalator to heaven in ${ self.path.name }")
      case RestartMe =>
        logAndCount(RestartMe)
        throw new RestartMeEx(s"and try again in ${ self.path.name }")
      case ResumeMe =>
        logAndCount(ResumeMe)
        throw new ResumeMeEx(s"as if nothing happened in ${ self.path.name }")
      case StopMe =>
        logAndCount(StopMe)
        throw new StopMeEx(s"hammertime! in ${ self.path.name }")
      case Hammertime =>
        logAndCount(Hammertime)
        context.stop(self)
      case GetCount => sender() ! receivedMsg
      case x: Any   => logAndCount(x)
    }
  }

  object SimpleWorkerSupervisor {
    case object GetWorker
  }

  class SimpleWorkerSupervisor(workerProps: Props)
      extends Actor
      with ActorLogging {

    val wrkr = context.actorOf(
      workerProps,
      s"worker-${ Random.alphanumeric.take(3).mkString }"
    )

    override def receive: Receive = {
      case GetWorker => sender() ! wrkr
      case x: Any    => wrkr forward x
    }
  }

  class EscalateThisEx(msg: String) extends Exception(msg)
  class RestartMeEx(msg: String)    extends Exception(msg)
  class ResumeMeEx(msg: String)     extends Exception(msg)
  class StopMeEx(msg: String)       extends Exception(msg)

  // type Decider = PartialFunction[Throwable, Directive]

  def loggingRestartOneForOneStrategy(log: LoggingAdapter) =
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 3 seconds) {
      case x: Throwable =>
        log.info(s"child failed with $x, restarting")
        Restart
    }

  def customStrategy(log: LoggingAdapter) =
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 3 seconds) {
      case res: ResumeMeEx =>
        log.info(s"child failed with $res, resuming")
        Resume
      case rest: RestartMeEx =>
        log.info(s"child failed with $rest, restarting")
        Restart
      case stp: StopMeEx =>
        log.info(s"child failed with $stp, stopping")
        Stop
      case e: EscalateThisEx =>
        log.info(s"child failed with $e, escalating")
        Escalate
    }

  class StrategicWorkerSupervisor(workerProps: Props)
      extends SimpleWorkerSupervisor(workerProps) {
    override val supervisorStrategy = customStrategy(log)
  }

  // the actor is receiving the potentially dangerous msg in the preRestart callback

  class Worker2 extends Worker {

    override def preRestart(reason: Throwable, message: Option[Any]) {
      message match {
        case Some(x) =>
          log.info(s"died on $x the last time, resending to self")
          self ! x
        case None => log.info("nothing to do on preRestart, proceeding")
      }
    }
  }
}
