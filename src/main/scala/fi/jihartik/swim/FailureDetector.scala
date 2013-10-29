package fi.jihartik.swim

import akka.actor.{ActorLogging, Cancellable, Actor, ActorRef}
import java.util.concurrent.atomic.AtomicLong
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import scala.concurrent.duration._

class FailureDetector(cluster: ActorRef, udp: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  val probeSeqNo = new AtomicLong(0)
  var outstandingProbes = Map[Long, OutstandingProbe]()

  override def preStart = context.system.scheduler.schedule(Config.probeInterval, Config.probeInterval, cluster, NeedMembersForProbing)

  def receive = {
    case ProbeMembers(members) => probeMembers(members)
    case ProbeIndirectly(forwarders, seqNo) => probeIndirectly(forwarders, seqNo)
    case Ping(seqNo) => sender ! Ack(seqNo)
    case IndirectPing(seqNo, target) => forwardPing(seqNo, target)
    case Ack(seqNo) => handleAck(seqNo)
    case AckTimedOut(seqNo) => handleAckTimeout(seqNo)
    case IndirectAckTimedOut(seqNo) => handleIndirectAckTimeout(seqNo)
  }

  def probeMembers(members: List[Member]) {
    val probedMembers = Util.takeRandom(members, Config.probedMemberCount)
    probedMembers.foreach(sendPing(_))
  }

  def sendPing(target: Member, originalSender: Option[ActorRef] = None, originalSeqNo: Option[Long] = None) {
    val seqNo = probeSeqNo.getAndIncrement
    val task = context.system.scheduler.scheduleOnce(Config.ackTimeout, self, AckTimedOut(seqNo))
    outstandingProbes += (seqNo -> OutstandingProbe(task, target, originalSender, originalSeqNo))
    udp ! SendMessage(target, Ping(seqNo))
  }

  def forwardPing(seqNo: Long, target: Member) {
    val originalSender = sender
    sendPing(target, Some(originalSender), Some(seqNo))
  }

  def handleAck(seqNo: Long) {
    outstandingProbes.get(seqNo).foreach {
      case OutstandingProbe(timer, _, Some(originalSender), Some(originalSeqNo)) => originalSender ! Ack(originalSeqNo); timer.cancel()
      case OutstandingProbe(timer, _, _, _) => timer.cancel()
    }
    outstandingProbes -= seqNo
  }

  def handleAckTimeout(seqNo: Long) {
    outstandingProbes.get(seqNo).foreach {
      case OutstandingProbe(_, target, None, None) => startIndirectProbe(seqNo, target)
      case _ => outstandingProbes -= seqNo  // Indirect ack timed out, just remove probe. Original sender will suspect the target.
    }
  }

  def startIndirectProbe(seqNo: Long, target: Member) {
    def createIndirectProbe(members: List[Member]) = ProbeIndirectly(Util.takeRandom(members.filter(_.name != target.name), Config.indirectProbeCount), seqNo)
    cluster.ask(NeedMembersForIndirectProbing).mapTo[List[Member]].map(createIndirectProbe).pipeTo(self)
  }

  def probeIndirectly(forwarders: List[Member], seqNo: Long) {
    outstandingProbes.get(seqNo).foreach { probe =>
      val task = context.system.scheduler.scheduleOnce(Config.ackTimeout, self, IndirectAckTimedOut(seqNo))
      outstandingProbes += (seqNo -> probe.copy(timeoutTimer = task))
      forwarders.foreach(f => udp ! SendMessage(f, IndirectPing(seqNo, probe.target)))
    }
  }

  def handleIndirectAckTimeout(seqNo: Long) {
    outstandingProbes.get(seqNo).foreach(probe => cluster ! SuspectMember(probe.target))
    outstandingProbes -= seqNo
  }

  case class AckTimedOut(seqNo: Long)
  case class IndirectAckTimedOut(seqNo: Long)
  case class OutstandingProbe(timeoutTimer: Cancellable, target: Member, originalSender: Option[ActorRef] = None, originalSeqNo: Option[Long] = None)
  case class ProbeIndirectly(forwarders: List[Member], seqNo: Long)
}
