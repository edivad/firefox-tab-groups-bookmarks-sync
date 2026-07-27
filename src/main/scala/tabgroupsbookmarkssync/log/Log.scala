package tabgroupsbookmarkssync.log

import scala.concurrent.Future

object Log {

  def apply(source: Source, message: String): Unit =
    println(s"[$source] $message")

  def future(source: Source, message: String): Future[Unit] =
    Future.successful {
      Log(source, message)
    }

}
