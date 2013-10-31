package fi.jihartik.swim.acceptance

import fi.jihartik.swim._
import akka.actor.ActorRef
import fi.jihartik.swim.SuspectMember
import fi.jihartik.swim.Member

class NodeSpec extends NodeTesting {
  "Node" should {
    "refute own suspicion with increased incarnation number" in {
      testRefuting(SuspectMember)
    }
    "refute own death with increased incarnation number" in {
      testRefuting(DeadMember)
    }
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
