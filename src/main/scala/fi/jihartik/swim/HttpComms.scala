package fi.jihartik.swim

import akka.actor.{Actor, Props, ActorRef}
import spray.can.Http
import akka.io.IO
import spray.routing.HttpServiceActor
import java.net.InetSocketAddress
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport._


class HttpComms(node: ActorRef, bindAddress: InetSocketAddress) extends Actor {
  import context.dispatcher
  import JsonSerialization._

  val httpPipeline = sendReceive

  override def preStart() = IO(Http)(context.system) ! Http.Bind(self, bindAddress, 100, Nil, None)

  def receive = {
    case Http.Connected(_, _) => sender ! Http.Register(context.system.actorOf(Props(new HttpHandler(node))))
    case PushMembers(to, members) => (httpPipeline ~> unmarshal[List[Member]]).apply(sendMembersRequest(to, members)).map(NewMembers) pipeTo node
  }

  private def sendMembersRequest(to: InetSocketAddress, members: List[Member]) = Post(s"http://${to.getHostName}:${to.getPort}/members", members)
}

class HttpHandler(node: ActorRef) extends HttpServiceActor {
  import context.dispatcher
  import JsonSerialization._
  implicit val timeout = Timeout(5.seconds)

  def receive = runRoute {
    path("members") {
      post {
        entity(as[List[Member]]) { members =>
          complete {
            node.ask(NewMembers(members)).mapTo[List[Member]]
          }
        }
      } ~
      get {
        complete {
          node.ask(NewMembers(Nil)).mapTo[List[Member]]
        }
      }
    }
  }
}

case class PushMembers(to: InetSocketAddress, members: List[Member])
