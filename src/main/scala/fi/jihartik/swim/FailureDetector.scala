package fi.jihartik.swim

import akka.actor._
import java.util.concurrent.atomic.AtomicLong

object ProbeSeqNo {
  private val seqNo = new AtomicLong(0)
  def next = seqNo.getAndIncrement
}

trait Pinger extends Actor with ActorLogging {
  import context.dispatcher

  val seqNo = ProbeSeqNo.next

  override def preStart() = self ! Start
  override def postStop() = udp ! UnregisterReceiver(self)

  def receive = {
    case Start => {
      udp ! RegisterReceiver(self)
      val task = context.system.scheduler.scheduleOnce(Config.ackTimeout, self, AckTimedOut)
      sendPings
      context.become(waitingForAck(task))
    }
  }

  def waitingForAck(ackTimer: Cancellable): Receive = {
    case Ack(s) if(s == seqNo) => {
      ackTimer.cancel()
      ackReceived
      context.stop(self)
    }
    case AckTimedOut => {
      ackTimedOut
      context.stop(self)
    }
  }

  def udp: ActorRef
  def sendPings: Unit
  def ackReceived: Unit = {}
  def ackTimedOut: Unit = {}

  case object Start
  case object AckTimedOut
}


class DirectPinger(receiver: ActorRef, target: Member, possibleForwarders: List[Member], val udp: ActorRef) extends Pinger {
  def sendPings = udp ! SendMessage(target, Ping(seqNo))
  override def ackTimedOut = {
    // Note context.system! IndirectPinger must not be created as a child of DirectPinger as it will terminate after its timeout and thus kill it children
    context.system.actorOf(Props(classOf[IndirectPinger], receiver, target, possibleForwarders, udp))
  }
}


class IndirectPinger(receiver: ActorRef, target: Member, forwarders: List[Member], val udp: ActorRef) extends Pinger {
  def sendPings = forwarders.foreach(fwd => udp ! SendMessage(fwd, IndirectPing(seqNo, target)))
  override def ackTimedOut = receiver ! SuspectMember(target)
}


class ForwardPinger(receiver: ActorRef, target: Member, originalSeqNo: Long, val udp: ActorRef) extends Pinger {
  def sendPings = udp ! SendMessage(target, Ping(seqNo))
  override def ackReceived = receiver ! Ack(originalSeqNo)
}


class FailureDetector(udp: ActorRef) extends Actor with ActorLogging {

  override def preStart() = udp ! RegisterReceiver(self)
  override def postStop() = udp ! UnregisterReceiver(self)

  def receive = {
    case ProbeMembers(members) => probeMembers(members)
    case Ping(seqNo) => sender ! Ack(seqNo)
    case IndirectPing(seqNo, target) => forwardPing(seqNo, target)
  }

  def probeMembers(members: List[Member]) {
    def forwardersFor(target: Member) = Util.takeRandom(members.filterNot(_.name == target.name), Config.indirectProbeCount)

    val probedMembers = Util.takeRandom(members, Config.probedMemberCount)
    probedMembers.foreach(target => context.actorOf(Props(classOf[DirectPinger], sender, target, forwardersFor(target), udp)))
  }

  def forwardPing(seqNo: Long, target: Member) = context.actorOf(Props(classOf[ForwardPinger], sender, target, seqNo, udp))
}
