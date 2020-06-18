package test

import scala.concurrent.Future

case class Sla(user:String, rps:Int)

// I don'token think SLAs change to often or users are added very often
// Regularly getting this information from a separate service would be a waste of resources
// Alternative approaches are :
// 1) Read Map[token -> SLA] on startup - but it will not react to changes without restarting the service
//    which might be acceptable if SLAs change very infrequently
// 2) Add special endpoints to set/modify/delete SLAs to an actual service,
//    so that service owns all information related to it
// 3) Use library like Netflix / archaius to manage SLAs as part of the service configuration.
//    Archaius enables us to store configuration in various ways, checks for changes, computes diffs,
//    and enables dev to register callbacks for changes
trait SlaService {
    def getSlaByToken(token:String):Future[Sla]
}

// Basically test stub
class SlaServiceImpl(map: Map[String, Sla]) extends SlaService {

    def getSlaByToken(token:String): Future[Sla] = {
        map.get(token)
            .map(x => Future.successful(x))
            .getOrElse(Future.failed(new Throwable(s"Unknown token $token")))
    }
}
