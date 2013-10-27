package fi.jihartik.swim

import akka.actor.{Props, Actor}
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

class Node(host: String, port: Int) extends Actor {
  import context.dispatcher

  val localAddress = new InetSocketAddress(host, port)
  val localName = s"Node $host"
  val udp = context.actorOf(Props(classOf[UdpComms], self, localAddress))
  val http = context.actorOf(Props(classOf[HttpComms], self, localAddress))
  val failureDetector = context.actorOf(Props(classOf[FailureDetector], self, udp))

  val incarnationNo = new AtomicLong(0)
  var state = ClusterState(localName, Map(localName -> Member(localName, host, port, Alive, incarnationNo.getAndIncrement)))

  def receive = {
    case p: Ping => failureDetector forward p
    case a: Ack => failureDetector forward a
    case NeedMembersForProbing => sender ! ProbeMembers(state.notDeadRemotes)

    case ReceiveMembers(newMembers) => {
      sender ! state.members
      mergeMembers(newMembers)
    }
    case AliveMember(member) => handleAlive(member)
    case SuspectMember(member) => (refute orElse suspectMember orElse ignore)(member)
    case ConfirmSuspicion(member) => confirmSuspicion(member)
    case DeadMember(member) => (refute orElse announceDead orElse ignore)(member)

    case Join(host) => http ! PushMembers(host, state.members)
  }

  def mergeMembers(remoteMembers: List[Member]) {
    remoteMembers.foreach {
      case remote if(state.alreadyKnown(remote)) => // Already known, do nothing
      case remote => remote.state match {
        case Alive => self ! AliveMember(remote)
        case Suspect | Dead => self ! SuspectMember(remote)
      }
    }
  }

  def handleAlive(member: Member) {
    if(state.hasWeakerIncarnationFor(member)) {
      println("Alive: " + member)
      broadcast(AliveMember(member))
      state += member
    }
  }

  def suspectMember: PartialFunction[Member, Unit] = {
    case member if(state.isAlive(member)) => {
      println("Suspect: " + member)
      broadcast(SuspectMember(member))
      state += member.copy(state = Suspect)
      context.system.scheduler.scheduleOnce(Config.suspectPeriod, self, ConfirmSuspicion(member))
    }
  }

  def confirmSuspicion(member: Member) {
    if(state.isSuspected(member)) self ! DeadMember(member)  // Member has not been able to refute and is still suspected -> mark as dead
  }

  def announceDead: PartialFunction[Member, Unit] = {
    case member if(state.isNotDead(member)) => {
      broadcast(DeadMember(member))
      state -= member
      println("Dead: " + member)
    }
  }

  def refute: PartialFunction[Member, Unit] = {
    case offendingMember if (state.hasSameOrWeakerIncarnationFor(offendingMember) && state.isUs(offendingMember)) => {
      incarnationNo.set(offendingMember.incarnation + 1)  // beat offending incarnation
      state = state.updateOurIncarnation(incarnationNo.get)
      println("Refuting: " + state.us)
      broadcast(AliveMember(state.us))  // Refute our suspicion / death
    }
  }

  def ignore: PartialFunction[Member, Unit] = { case _ => Unit }
  def broadcast(message: UdpMessage) = udp ! Broadcast(state.remotes, message)

  case class ConfirmSuspicion(member: Member)
}


case class Join(host: InetSocketAddress)
case class ProbeMembers(members: List[Member])
case object NeedMembersForProbing
case class ReceiveMembers(members: List[Member])
