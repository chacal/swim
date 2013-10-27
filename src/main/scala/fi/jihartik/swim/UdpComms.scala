package fi.jihartik.swim

import akka.actor.{Actor, ActorRef}
import java.net.InetSocketAddress
import akka.io.{Udp, IO}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._


class UdpComms(controller: ActorRef, bindAddress: InetSocketAddress) extends Actor {
  import context.system
  import context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  override def preStart() = IO(Udp) ! Udp.Bind(self, bindAddress)

  def receive = {
    case Udp.Bound(_) => context.become(ready(sender))
  }

  def ready(ioActor: ActorRef): Receive = {
    case SendMessage(member, msg) => send(ioActor, member, msg)
    case Broadcast(members, msg) => members.foreach(send(ioActor, _, msg))
    case Udp.Received(data, from) => (controller ? UdpMessage(data)).mapTo[UdpMessage].foreach(reply => send(ioActor, from, reply))
}

  def send(ioActor: ActorRef, to: Member, message: UdpMessage): Unit = send(ioActor, addressFor(to), message)
  def send(ioActor: ActorRef, to: InetSocketAddress, message: UdpMessage) = ioActor ! Udp.Send(message.toByteString, to)
  def addressFor(member: Member) = new InetSocketAddress(member.ip, member.port)
}


case class SendMessage(to: Member, message: UdpMessage)
case class Broadcast(to: Iterable[Member], message: UdpMessage)