package fi.jihartik.swim

import akka.actor.{Actor, Props, ActorSystem}
import java.net.InetSocketAddress


object Node1 extends App {
  val system = ActorSystem()
  system.actorOf(Props(classOf[Node], "127.0.0.1", 10001))
}

object Node2 extends App {
  val system = ActorSystem()
  val node = system.actorOf(Props(classOf[Node], "127.0.0.2", 10002))
  Thread.sleep(1000)
  node ! Join("127.0.0.1", 10001)
}


object Node3 extends App {
  val system = ActorSystem()
  val node = system.actorOf(Props(classOf[Node], "127.0.0.3", 10003))
  Thread.sleep(1000)
  node ! Join("127.0.0.1", 10001)
}




object MultiNode extends App {
  val system = ActorSystem()
  val max = args(0).toInt
  (20 until 20 + max).par.foreach { i =>
    val node = system.actorOf(Props(classOf[Node], s"127.0.0.$i", 10000 + i))
    Thread.sleep(500)
    node ! Join("127.0.0.1", 10001)
  }
}