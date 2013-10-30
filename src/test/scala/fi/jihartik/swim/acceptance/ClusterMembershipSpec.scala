package fi.jihartik.swim.acceptance

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import akka.actor.{ActorRef, Props, ActorSystem}
import fi.jihartik.swim._
import java.net.ServerSocket
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await
import fi.jihartik.swim.Member


class ClusterMembershipSpec extends WordSpec with Eventually with Matchers with IntegrationPatience with BeforeAndAfterAll {
  val system = ActorSystem()
  implicit val timeout = new Timeout(5.seconds)
  val host = "127.0.0.1"

  override protected def afterAll = {
    system.shutdown()
    system.awaitTermination()
  }

  "Cluster" should {
    "join nodes properly" in {
      val (port1, node1) = startNode()
      val (port2, node2) = startNode()
      node2 ! Join(host, port1)
      assertNotDeadClusterSize(List(node1, node2), 2)

      val (_, node3) = startNode()
      node3 ! Join(host, port2)
      assertNotDeadClusterSize(List(node1, node2, node3), 3)
    }
    "notice failing node" in {
      val (port1, node1) = startNode()
      val (_, node2) = startNode()
      val (_, node3) = startNode()
      node2 ! Join(host, port1)
      node3 ! Join(host, port1)
      assertNotDeadClusterSize(List(node1, node2, node3), 3)
      system.stop(node2)
      assertNotDeadClusterSize(List(node1, node3), 2)
    }
  }

  def assertNotDeadClusterSize(nodes: List[ActorRef], clusterSize: Int) = eventually {
    nodes.foreach { node => getMembersFrom(node).filterNot(_.state == Dead).size should be(clusterSize) }
  }

  def startNode(host: String = host, port: Int = RandomPort()) = {
    (port, system.actorOf(Props(classOf[Node], host, port)))
  }
  def getMembersFrom(node: ActorRef) = Await.result(node.ask(GetMembers).mapTo[List[Member]], timeout.duration)
}


object RandomPort {
  def apply() = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }
}