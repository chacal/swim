package fi.jihartik.swim.acceptance

import java.net.ServerSocket
import org.scalatest.{Matchers, BeforeAndAfterAll, WordSpec}
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import akka.actor.{ActorRef, Props, ActorSystem}
import fi.jihartik.swim.{Dead, Member, GetMembers, Node}
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout

trait NodeTesting extends WordSpec with BeforeAndAfterAll with Eventually with Matchers with IntegrationPatience {
  val system = ActorSystem()
  implicit val timeout = new Timeout(5.seconds)
  val host = "127.0.0.1"

  override protected def afterAll = {
    system.shutdown()
    system.awaitTermination()
  }

  def startNode(host: String = host, port: Int = RandomPort()) = {
    (port, system.actorOf(Props(classOf[Node], host, port, TestConfig)))
  }

  def getMembersFrom(node: ActorRef) = Await.result(node.ask(GetMembers).mapTo[List[Member]], timeout.duration)
  def assertNotDeadClusterSize(nodes: List[ActorRef], clusterSize: Int) = eventually {
    nodes.foreach { node => getMembersFrom(node).filterNot(_.state == Dead).size should be(clusterSize) }
  }
}

object RandomPort {
  def apply() = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }
}
