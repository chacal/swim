package fi.jihartik.swim.acceptance

import fi.jihartik.swim._

class ClusterMembershipSpec extends NodeTesting {

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
    "notice failing node and re-join it" in {
      val (port1, node1) = startNode()
      val (port2, node2) = startNode()
      val (_, node3) = startNode()
      node2 ! Join(host, port1)
      node3 ! Join(host, port1)
      assertNotDeadClusterSize(List(node1, node2, node3), 3)

      node2 ! Stop
      assertNotDeadClusterSize(List(node1, node3), 2)
      val (_, newNode2) = startNode(port = port2)
      newNode2 ! Join(host, port1)
      assertNotDeadClusterSize(List(node1, newNode2, node3), 3)
    }
  }
}