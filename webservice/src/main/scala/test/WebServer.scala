package test

import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.function.{BiConsumer, BiFunction}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn
import collection.JavaConverters._

object WebServer extends App {

    val config = Configuration(
        slaServiceTimeoutInSeconds = 1,
        maxConcurrentSlaRequests = 10,
        numTimeslotsPerSecond = 10,
        graceRps = 1000)

    val tokensMap = (0 until 10).map { i =>
        s"token$i" -> Sla(s"user$i", 100 + 50 * i)
    }.toMap

    val slaService: SlaService = new SlaServiceImpl(tokensMap)
    val throttlingService: ThrottlingService = new ThrottlingServiceImpl(slaService, config)

    // for monitoring purposes
    @volatile
    var perTokenRPS = new ConcurrentHashMap[String, Integer]()

    val timer = new java.util.Timer().scheduleAtFixedRate(new TimerTask {
        override def run(): Unit = {
            val oldMap = perTokenRPS
            perTokenRPS = new ConcurrentHashMap[String, Integer]()
            println("============================================================")
            oldMap.asScala.toArray.sortBy(_._1)
                .foreach {case (t,u) =>  println(s"$t - $u")}
        }
    }, 1000, 1000)


    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher


    val route =
        path("hello") {
            get {
                optionalHeaderValueByName("token") { tokenOpt =>
                    complete({
                        if (throttlingService.isRequestAllowed(tokenOpt)) {
                            perTokenRPS.compute(tokenOpt.getOrElse("Unauthorized"), new BiFunction[String, Integer, Integer] {
                                override def apply(t: String, u: Integer): Integer = {
                                    if (u == null) 1
                                    else u + 1
                                }
                            })
                            HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
                        } else {
                            HttpResponse(StatusCodes.BandwidthLimitExceeded)
                        }
                    })
                }
            }
        }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done
}