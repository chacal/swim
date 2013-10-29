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
  val udp = context.actorOf(Props(classOf[UdpComms], self, localAddress))
  val http = context.actorOf(Props(classOf[HttpComms], self, localAddress))
  val cluster = context.actorOf(Props(classOf[Cluster], host, port, udp))
  val failureDetector = context.actorOf(Props(classOf[FailureDetector], cluster, udp))


  def receive = {
    case Join(host) => cluster.ask(GetMembers).mapTo[List[Member]].map(PushMembers(host, _)).pipeTo(http)
    case msg: ReceiveMembers => cluster forward msg
    case msg: FailureDetectionMessage => failureDetector forward msg
    case msg: UdpMessage => cluster forward msg
  }
}
