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
      lib.akkaActor
      , lib.akkaCluster
      , lib.akkaClusterMetrics
      , lib.akkaClusterTools
      , lib.akkaStream
      , lib.akkaHttp
      , lib.akkaClusterSharding
      , lib.akkaPersistence
  //		, lib.sigarLoader
    )
  )
  .dependsOn(
    api
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
		val akkaPersistenceCassandra = "0.80-RC2"

		val sigarLoader = "1.6.6-rev002"
	}

	val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
	val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Version.akka
	val akkaClusterMetrics = "com.typesafe.akka" %% "akka-cluster-metrics" % Version.akka
	val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
	val akkaHttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
	val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
	val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Version.akka
	val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Version.akka
	val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % Version.akkaPersistenceCassandra

	val sigarLoader = "io.kamon" % "sigar-loader" % Version.sigarLoader

}

