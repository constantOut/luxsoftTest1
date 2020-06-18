name := "luxsoftTest1"

version := "0.1"

scalaVersion := "2.12.4"

lazy val rootProject = project.in(file("."))
.aggregate(webservice, loadgen)

lazy val webservice = project.settings(name := "webservice")

lazy val loadgen = project.settings(name := "loadgen")