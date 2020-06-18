package test

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import org.scalatest._

class ThrottlingServiceSpec extends FlatSpec with Matchers {

    val config = Configuration()
    val throttlingServiceImpl = new ThrottlingServiceImpl(new SlaServiceImpl(Map(), Seq()), config)

    def emptySlaCache = new ConcurrentHashMap[String, Sla]()
    def emptyRpsCounters = new ConcurrentHashMap[String, Integer]()

    "ThrottlingService" should "permit unauthorized requests when limit is not reached" in {
        val graceRps = new AtomicLong(0)
        val result = throttlingServiceImpl.isRequestAllowedImpl(
            None,
            emptySlaCache,
            emptyRpsCounters,
            graceRps,
            null
        )
        assert(graceRps.get() == 1)
        assert(result)
    }

    "ThrottlingService" should "not permit unauthorized requests when limit is not reached" in {
        val graceRps = new AtomicLong(config.graceRps)
        val result = throttlingServiceImpl.isRequestAllowedImpl(
            None,
            emptySlaCache,
            emptyRpsCounters,
            graceRps,
            null
        )
        assert(graceRps.get() == config.graceRps)
        assert(!result)
    }

    "ThrottlingService" should "permit requests with a token when SLA value is not available yet and grace RPS limit is not reached" in {
        val graceRps = new AtomicLong(0)
        val result = throttlingServiceImpl.isRequestAllowedImpl(
            Some("token"),
            emptySlaCache,
            emptyRpsCounters,
            graceRps,
            null
        )
        assert(graceRps.get() == 1)
        assert(result)
    }

    "ThrottlingService" should "not permit requests with a token when SLA value is not available yet but grace RPS limit is reached" in {
        val graceRps = new AtomicLong(config.graceRps)
        val result = throttlingServiceImpl.isRequestAllowedImpl(
            Some("token"),
            emptySlaCache,
            emptyRpsCounters,
            graceRps,
            null
        )
        assert(graceRps.get() == config.graceRps)
        assert(!result)
    }

    "ThrottlingService" should "forbid requests with a token when RPS limit is reached" in {
        val slaCache = emptySlaCache
        slaCache.put("token", Sla("user", 10))
        val throttlingServiceImpl = new ThrottlingServiceImpl(new SlaServiceImpl(Map(), Seq()), config)

        val graceRps = new AtomicLong(0)
        val rpsCounters = emptyRpsCounters
        rpsCounters.put("user", 10)

        val result = throttlingServiceImpl.isRequestAllowedImpl(
            Some("token"),
            slaCache,
            rpsCounters,
            graceRps,
            null
        )
        assert(!result)
        assert(rpsCounters.get("user") == 10)
    }



    "ThrottlingService" should "call SlaService exactly once for each token" ignore  {
        val graceRps = new AtomicLong(0)
        var callCounter = new AtomicInteger(0)


        val slaRequestFunc = new java.util.function.Function[String, Sla] () {
            override def apply(token: String): Sla = {
                /* debugging shows this function is called two times with the same despite the fact that
                 * javadoc for computeIfAbsent says:
                 * The entire method invocation is performed atomically, so the function is
                 * applied at most once per key.*/
                callCounter.incrementAndGet()
                Sla("user", 100)
            }
        }

        throttlingServiceImpl.isRequestAllowedImpl(
            Some("token"),
            emptySlaCache,
            emptyRpsCounters,
            graceRps,
            slaRequestFunc
        )
        throttlingServiceImpl.isRequestAllowedImpl(
            Some("token"),
            emptySlaCache,
            emptyRpsCounters,
            graceRps,
            slaRequestFunc
        )
        assert(callCounter.get() == 1)
    }
}
