import java.util.TimerTask

import scalaj.http._

object Run extends App {
    val numThreads = 8
    val testDuration = 60
    val numTokens = 10

    val timer = new java.util.Timer().schedule(new TimerTask {
        override def run(): Unit = {
            System.exit(0)
        }
    }, testDuration * 1000L)

    val tokens = ((0 until numTokens).map { i =>
        Some(s"token$i")
    } ++ Seq(None)).toArray

    val baseRequest = Http("http://127.0.0.1:8080/hello")
    (0 until numThreads).foreach { i =>
        while(true)
            (0 until numTokens + 1).foreach { tokenIndex =>
            val response: HttpResponse[String] = {
                tokens(tokenIndex) match {
                    case Some(token) => baseRequest.header("token", token)
                    case None => baseRequest
                }

            }.asString
        }
    }
}
