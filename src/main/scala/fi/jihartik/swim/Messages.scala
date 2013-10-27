package fi.jihartik.swim

import akka.util.ByteString
import spray.json._


trait UdpMessage {
  def toByteString: ByteString
}

case class Ping(seqNo: Long) extends UdpMessage {
  def toByteString = ByteString(s"0$seqNo")
}
case class Ack(seqNo: Long) extends UdpMessage {
  def toByteString = ByteString(s"1$seqNo")
}
abstract class MemberMessage(msgType: Int) extends UdpMessage {
  import JsonSerialization._
  def member: Member
  def toByteString = ByteString(s"$msgType${member.toJson.compactPrint}")
}
case class AliveMember(member: Member) extends MemberMessage(2)
case class SuspectMember(member: Member) extends MemberMessage(3)
case class DeadMember(member: Member) extends MemberMessage(4)


object UdpMessage {
  import JsonSerialization._

  def apply(payload: ByteString): UdpMessage = {
    val decoded = payload.decodeString("UTF-8")
    val msgType = decoded.take(1).toInt
    val message = decoded.drop(1)
    msgType match {
      case 0 => Ping(message.toLong)
      case 1 => Ack(message.toLong)
      case 2 => AliveMember(message.asJson.convertTo[Member])
      case 3 => SuspectMember(message.asJson.convertTo[Member])
      case 4 => DeadMember(message.asJson.convertTo[Member])
    }
  }
}
