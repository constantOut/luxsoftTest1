package test

import java.lang.Throwable
import java.util.concurrent.atomic.AtomicLong
import java.util.{TimerTask, UUID}
import java.util.concurrent.{ConcurrentHashMap, Executors}
import java.util.function.BiFunction

import com.typesafe.scalalogging.{LazyLogging, Logger}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class Configuration(slaServiceTimeoutInSeconds: Int = 10,
                         maxConcurrentSlaRequests: Int = 100,
                         numTimeslotsPerSecond: Int = 10,
                         graceRps: Int = 1000 // configurable
                        )

trait ThrottlingService {
    val config: Configuration
    val slaService: SlaService // use mocks/stubs for testing
    // Should return true if the request is within allowed RPS.
    def isRequestAllowed(token: Option[String]): Boolean
}

/* It is possible that one unauthorized user will prevent other user from login in,
   More realistic Throttling service should also limit RPS by IP for unauthorized users.
  */
class ThrottlingServiceImpl(val slaService: SlaService,
                            val config: Configuration = Configuration()) extends ThrottlingService with LazyLogging {

    // Token -> Sla
    val slaCache = new ConcurrentHashMap[String, Sla]()

    val slaRequestFunc = new java.util.function.Function[String, Sla] () {
        override def apply(token: String): Sla = {
            val f = slaService.getSlaByToken(token)
            Try(Await.result(f, config.slaServiceTimeoutInSeconds second)) match {
                case Success(x) => x
                case Failure(throwable) =>
                    // will try to get SLA on next request with this token
                    logger.error("Call to SLA service failed", throwable)
                    null
            }
        }
    }

    val slaRequestsEC = ExecutionContext.fromExecutorService(
        Executors.newFixedThreadPool(config.maxConcurrentSlaRequests))

    // User -> Count
    val rpsCounters = new ConcurrentHashMap[String, Integer]()

    val graceActualRps = new AtomicLong(0l)

    /* Requirement 5. SLA should be counted by intervals of 1/10 second (i.e. if RPS
        limit is reached, after 1/10 second ThrottlingService should allow
        10% more requests)

        This reads like reset actual RPS counter exactly 100ms after the RPS limit is reached,
        which would result in service being unavailable for 100ms.
        What I believe you meant is: allow no more than 10% of RPS in a single 100ms timeslot.
    */

    val timer = new java.util.Timer().scheduleAtFixedRate(new TimerTask {
        override def run(): Unit = {
            rpsCounters.clear()
            graceActualRps.set(0l)
        }
    }, 100, 100)

    def isRequestAllowed(tokenOpt: Option[String]): Boolean = isRequestAllowedImpl(tokenOpt, slaCache, rpsCounters, graceActualRps, slaRequestFunc)

    def isRequestAllowedImpl(tokenOpt: Option[String],
                             slaCache: ConcurrentHashMap[String, Sla],
                             rpsCounters: ConcurrentHashMap[String, Integer],
                             graceActualRps: AtomicLong,
                             slaRequestFunc: java.util.function.Function[String, Sla]) = tokenOpt.map { token =>
        Option(slaCache.get(token)) match {
            case Some(sla) => handleUser(sla, rpsCounters)
            case None => {
                Future {
                    slaCache.computeIfAbsent(token, slaRequestFunc)
                } (slaRequestsEC)
                // first request for a token will be processed without waiting for SLA service
                handleGrace(graceActualRps) // it's possible to perform rate limiting by ip at this point
            }
        }
    }.getOrElse(handleGrace(graceActualRps))

    def handleUser(sla: Sla, rpsCounters: ConcurrentHashMap[String, Integer]): Boolean = {
        val actualRPS = rpsCounters.getOrDefault(sla.user, 0)
        if(actualRPS * config.numTimeslotsPerSecond < sla.rps) {
            rpsCounters.compute(sla.user, new BiFunction[String, Integer, Integer] {
                override def apply(t: String, u: Integer): Integer = if (u == null) 1 else u + 1
            })
            true
        } else false
    }

    def handleGrace(graceActualRps: AtomicLong): Boolean = {
        if(graceActualRps.get() * config.numTimeslotsPerSecond < config.graceRps) {
            graceActualRps.incrementAndGet()
            true
        } else false
    }
}
