package fi.jihartik.swim

import scala.util.Random

object Util {
  def takeRandom(member: List[Member], howMany: Int) = Random.shuffle(member).take(howMany)
}
