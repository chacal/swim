package fi.jihartik.swim

import akka.util.ByteString
import spray.json._


trait UdpMessage {
  def toByteString: ByteString
}
trait FailureDetectionMessage extends UdpMessage

case class Ping(seqNo: Long) extends FailureDetectionMessage {
  def toByteString = ByteString(s"0$seqNo")
}
case class IndirectPing(seqNo: Long, target: Member) extends FailureDetectionMessage {
  import JsonSerialization._
  def toByteString = ByteString(s"1$seqNo ${target.toJson.compactPrint}")
}
case class Ack(seqNo: Long) extends FailureDetectionMessage {
  def toByteString = ByteString(s"2$seqNo")
}
abstract class MemberMessage(msgType: Int) extends UdpMessage {
  import JsonSerialization._
  def member: Member
  def toByteString = ByteString(s"$msgType${member.toJson.compactPrint}")
}
case class AliveMember(member: Member) extends MemberMessage(3)
case class SuspectMember(member: Member) extends MemberMessage(4)
case class DeadMember(member: Member) extends MemberMessage(5)


object UdpMessage {
  import JsonSerialization._

  def apply(payload: ByteString): UdpMessage = {
    val decoded = payload.decodeString("UTF-8")
    val msgType = decoded.take(1).toInt
    val message = decoded.drop(1)
    msgType match {
      case 0 => Ping(message.toLong)
      case 1 => IndirectPing(message.takeWhile(_ != ' ').toLong, message.dropWhile(_ != ' ').drop(1).asJson.convertTo[Member])
      case 2 => Ack(message.toLong)
      case 3 => AliveMember(message.asJson.convertTo[Member])
      case 4 => SuspectMember(message.asJson.convertTo[Member])
      case 5 => DeadMember(message.asJson.convertTo[Member])
    }
  }
}
