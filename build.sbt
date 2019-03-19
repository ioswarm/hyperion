lazy val settings = Seq(
	name := "hyperion"
	, organization := "de.ioswarm"
	, version := "0.3.0"
	, scalaVersion := "2.12.8"
	, scalacOptions ++= Seq(
		"-language:_"
		, "-unchecked"
		, "-deprecation"
		, "-encoding", "UTF-8"
	)
)

lazy val hyperion = project.in(file("."))
	.settings(settings)
	.aggregate(
		core
		, api
	)

lazy val api = project.in(file("api"))
  .settings(settings)
  .settings(
		name := "hyperion-api"
		, libraryDependencies  ++= Seq(
			lib.akkaActor
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
  .dependsOn(
		api
	)


lazy val lib = new {
	object Version {
		val akka = "2.5.21"
		val akkaHttp = "10.1.7"

	}

	val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
	val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
	val akkaHttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp



}

