name := "webservice"

version := "0.1"

scalaVersion := "2.12.4"

resolvers += Resolver.url("typesafe", url("http://repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.ivyStylePatterns)
resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "com.typesafe.akka" %% "akka-actor" % "2.6.6",
    "com.typesafe.akka" %% "akka-http"   % "10.1.12",
    "com.typesafe.akka" %% "akka-stream" % "2.6.6"
)

scalacOptions := Seq(
    "-encoding", "UTF-8", "-target:jvm-1.8", "-deprecation",
    "-feature", "-unchecked", "-language:implicitConversions", "-language:postfixOps")