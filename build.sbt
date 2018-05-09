lazy val settings = Seq(
	name := "hyperion"
	, organization := "de.ioswarm"
	, version := "0.2.2"
	, scalaVersion := "2.12.4"
	, scalacOptions ++= Seq(
		"-language:_"
		, "-unchecked"
		, "-deprecation"
		, "-encoding", "UTF-8"
	)
)

lazy val hyperion = project.in(file("."))
	.settings(settings)
	.settings(
		name := "hyperion"
	)
	.aggregate(
		core
    , cassandra
		, auth
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
			, lib.akkaPersistenceQuery

      , lib.argonaut
      , lib.akkaHttpArgonaut

  		, lib.sigarLoader

      , lib.chill

      ,lib.apacheCLI
    )
  )
  .enablePlugins(
		spray.boilerplate.BoilerplatePlugin
  )

lazy val cassandra = project.in(file("cassandra"))
  .settings(settings)
  .settings(
    name := "hyperion-cassandra"
    , libraryDependencies ++= Seq(
      lib.akkaPersistenceCassandra
      , lib.cassie
      , lib.cassieAkkaStream
    )
  )
  .dependsOn(
    core
  )

lazy val auth = project.in(file("auth"))
  .settings(settings)
  .settings(
    name := "hyperion-auth"
    , libraryDependencies ++= Seq(
      lib.jbcrypt
    )
  )
  .dependsOn(
    core
    , cassandra
  )

lazy val lib = new {
	object Version {
		val akka = "2.5.8"
		val akkaHttp = "10.0.11"
		val akkaPersistenceCassandra = "0.59"

		val argonaut = "6.2"
    val akkaHttpArgonaut = "1.15.0"

		val sigarLoader = "1.6.6-rev002"

    val chill = "0.9.2"

		val cassie = "0.3.1"

    val jbcrypt = "0.4"

		val apacheCLI = "1.4"
	}

	val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
	val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Version.akka
	val akkaClusterMetrics = "com.typesafe.akka" %% "akka-cluster-metrics" % Version.akka
	val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
	val akkaHttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
	val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
	val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Version.akka
	val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Version.akka
	val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % Version.akka

	val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % Version.akkaPersistenceCassandra

  val argonaut = "io.argonaut" %% "argonaut" % Version.argonaut
  val akkaHttpArgonaut = "de.heikoseeberger" %% "akka-http-argonaut" % Version.akkaHttpArgonaut

	val sigarLoader = "io.kamon" % "sigar-loader" % Version.sigarLoader

  val chill = "com.twitter" %% "chill-akka" % Version.chill

  val cassie = "de.ioswarm" %% "cassie" % Version.cassie
	val cassieAkkaStream = "de.ioswarm" %% "cassie-akka-stream" % Version.cassie

	val jbcrypt = "org.mindrot" % "jbcrypt" % Version.jbcrypt

  val apacheCLI = "commons-cli" % "commons-cli" % Version.apacheCLI

}

