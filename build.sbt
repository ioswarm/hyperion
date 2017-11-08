lazy val settings = Seq(
	name := "hyperion"
	, organization := "de.ioswarm"
	, version := "0.1.0"
	, scalaVersion := "2.12.3"
	, scalacOptions ++= Seq(
		"-language:_"
		, "-unchecked"
		, "-deprecation"
		, "-encoding", "UTF-8"
		, "target:jvm-1.8"
	)
)

lazy val hyperion = project.in(file("."))
	.settings(settings)
	.settings(
		name := "hyperion"
	)
	.aggregate(
		api
		, core
		, auth
	)

lazy val api = project.in(file("api"))
.settings(settings)
.settings(
	name := "hyperion-api"
	, libraryDependencies ++= Seq(

	)
)

lazy val core = project.in(file("core"))
.settings(settings)
.settings(
	name := "hyperion-core"
	, libraryDependencies ++= Seq(
		lib.akkaStream
		, lib.akkaHttp
	)
)
.enablePlugins(
	BoilerplatePlugin
)

lazy val auth = project.in(file("auth"))
.settings(settings)
.settings(
	name := "hyperion-auth"
	, libraryDependencies ++= Seq(

	)
)

lazy val lib = new {
	object Version {
		val akka = "2.5.4"
		val akkaHttp = "10.0.10"
		val testplusPlay = "3.1.2"
	}

	val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
	val akkaHttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp

	val testplusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % Version.testplusPlay % Test

}

