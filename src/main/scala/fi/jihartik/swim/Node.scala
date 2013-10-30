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

  val broadcaster = context.actorOf(Props(classOf[Broadcaster], udp), "broadcaster")
  val failureDetector = context.actorOf(Props(classOf[FailureDetector], udp), "failure-detector")
  val cluster = context.actorOf(Props(classOf[Cluster], host, port, broadcaster, failureDetector), "cluster")
  udp ! RegisterReceiver(cluster)

  def receive = {
    case Join(host) => cluster.ask(GetMembers).mapTo[List[Member]].map(PushMembers(host, _)).pipeTo(http)
    case msg: NewMembers => cluster ! msg
    case msg @ GetMembers => cluster forward msg
  }
}
