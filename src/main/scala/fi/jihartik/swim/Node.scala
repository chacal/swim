package fi.jihartik.swim

import akka.actor.{Actor, Props}
import java.net.InetSocketAddress
import akka.pattern.ask
import akka.pattern.pipe
import scala.concurrent.duration._
import akka.util.Timeout


class Node(host: String, port: Int) extends Actor{
  implicit val timeout = Timeout(5.seconds)
  import context.dispatcher

  val localAddress = new InetSocketAddress(host, port)

  val udp = context.actorOf(Props(classOf[UdpComms], localAddress), "udp")
  val http = context.actorOf(Props(classOf[HttpComms], self, localAddress), "http")
  val failureDetector = context.actorOf(Props(classOf[FailureDetector], udp), "failure-detector")

  val broadcaster = context.actorOf(Props(classOf[Broadcaster], udp), "broadcaster")
  val cluster = context.actorOf(Props(classOf[Cluster], host, port, broadcaster), "cluster")
  udp ! RegisterReceiver(self)

  override def preStart = {
    Util.schedule(Config.probeInterval, self, TriggerProbes)
    Util.schedule(Config.broadcastInterval, self, TriggerBroadcasts)
  }

  def receive = {
    case Join(host) => getMembers.map(SendMembers(host, _)).pipeTo(http)
    case msg: ClusterStateMessage => cluster ! msg
    case msg @ GetMembers => getMembers pipeTo sender

    case TriggerProbes => {
      val sender = self
      getNotDeadRemotes.onSuccess { case members => failureDetector.tell(ProbeMembers(members), sender) }  // Use us as a sender to get timeouts back properly
    }
    case ProbeTimedOut(member) => cluster ! SuspectMember(member)

    case TriggerBroadcasts => getNotDeadRemotes.map(SendBroadcasts).pipeTo(broadcaster)
  }

  private def getMembers = cluster.ask(GetMembers).mapTo[List[Member]]
  private def getNotDeadRemotes = cluster.ask(GetNotDeadRemotes).mapTo[List[Member]]

  case object TriggerProbes
  case object TriggerBroadcasts
}
