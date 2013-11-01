package fi.jihartik.swim.acceptance

import fi.jihartik.swim._
import akka.actor.{Props, ActorRef}
import spray.client.pipelining._
import fi.jihartik.swim.SuspectMember
import fi.jihartik.swim.DeadMember
import fi.jihartik.swim.Member
import org.scalatest.concurrent.ScalaFutures
import java.net.InetSocketAddress
import akka.actor.ActorDSL._
import akka.pattern.ask

class NodeSpec extends NodeTesting with ScalaFutures {
  "Node" should {
    "refute own suspicion with increased incarnation number" in {
      testRefuting(SuspectMember)
    }
    "refute own death with increased incarnation number" in {
      testRefuting(DeadMember)
    }
    "provide members via HTTP GET" in {
      val (port1, _) = startNode()
      val httpPipeline = sendReceive
      val response = s"""[{"name":"Node 127.0.0.1:$port1","ip":"127.0.0.1","port":$port1,"state":"alive","incarnation":0}]"""
      httpPipeline(Get(s"http://$host:${port1}/members")).futureValue.entity.asString should be(response)
    }
    "broadcast cluster state changes configured times" in {
      val (_, node1) = startNode()
      val (port2, collector) = udpMessageCollector

      val aliveMessage = AliveMember(Member("TestNode", host, port2, Alive, 0))
      node1 ! aliveMessage  // "join" our collector -> node1 should start broadcasting this
      eventually { collectedClusterMessagesFrom(collector) should be(1.to(TestConfig.maxBroadcastTransmitCount).map(_ => aliveMessage)) }
    }
    "send indirect probes when direct probe times out" in {
      // Create cluster of two nodes & collector
      val (port1, node1) = startNode()
      val (port2, node2) = startNode()
      val (collectorPort, collector) = udpMessageCollector
      node2 ! Join(host, port1)
      node1 ! AliveMember(Member("TestNode", host, collectorPort, Alive, 0))
      assertNotDeadClusterSize(node1 :: node2 :: Nil, 3)

      node2 ! Stop

      // Node 1 should try to reach (now stopped) node 2 via collector
      eventually { collectedFailureDetectionMessagesFrom(collector).find {
        case IndirectPing(_, Member(_, _, `port2`, Alive, 0)) => true
        case _ => false
      } should be('defined) }
    }
  }

  def collectedClusterMessagesFrom(collector: ActorRef) = collector.ask("getClusterMessages").mapTo[List[ClusterStateMessage]].futureValue
  def collectedFailureDetectionMessagesFrom(collector: ActorRef) = collector.ask("getFailureDetectionMessages").mapTo[List[FailureDetectionMessage]].futureValue

  def udpMessageCollector = {
    val port = RandomPort()
    val udp = system.actorOf(Props(classOf[UdpComms], new InetSocketAddress(host, port)))
    system.actorOf(Props(classOf[FailureDetector], udp, TestConfig)) // Start failure detector to respond to pings and to keep us alive in the cluster
    val collectorActor = actor(new Act {
      val received = new scala.collection.mutable.ListBuffer[UdpMessage]
      become {
        case CompoundUdpMessage(messages) => messages.foreach(self ! _)  // Unpack compound messages
        case msg: UdpMessage =>  received += msg
        case "getClusterMessages" => sender ! received.toList.filter(_.isInstanceOf[ClusterStateMessage])
        case "getFailureDetectionMessages" => sender ! received.toList.filter(_.isInstanceOf[FailureDetectionMessage])
      }
    })
    udp ! RegisterReceiver(collectorActor)
    (port, collectorActor)
  }

  def testRefuting(reason: Member => ClusterStateMessage) {
    val (port1, node1) = startNode()
    val (_, node2) = startNode()
    node2 ! Join(host, port1)
    assertNotDeadClusterSize(node1 :: node2 :: Nil, 2)

    node1 ! reason(getNode(node1, port1).copy(incarnation = 5))
    eventually {
      val node = getNode(node2, port1)
      node.state should be(Alive)
      node.incarnation should be(6)
    }
  }

  def getNode(via: ActorRef, port: Int) = getMembersFrom(via).find(m => m.name.endsWith(s"$port")).get
}
