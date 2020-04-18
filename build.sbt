lazy val settings = Seq(
	name := "hyperion"
	, organization := "de.ioswarm"
	, version := "0.3.3-2"
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
		, cluster

		, mongo
	)

lazy val api = project.in(file("api"))
  .settings(settings)
  .settings(
		name := "hyperion-api"
		, libraryDependencies  ++= Seq(
			lib.akkaActor
			, lib.akkaRemote
			, lib.time
			, lib.runtimeLib
			, lib.scalaReflect

			, lib.argonaut
			, lib.timeArgonaut
		)
		, PB.targets in Compile := Seq(
			scalapb.gen() -> (sourceManaged in Compile).value
		)
		, PB.protoSources in Compile := Seq(file("api/src/main/resources/de/ioswarm/hyperion"))
	)

lazy val core = project.in(file("core"))
  .settings(settings)
  .settings(
		name := "hyperion-core"
		, libraryDependencies ++= Seq(
			lib.akkaStream
			, lib.akkaHttp
			, lib.akkaPersistence

			, lib.akkaSLF4J
			, lib.logback

			, lib.hseebergerArgonaut

			, lib.levelDB
		)
	)
  .enablePlugins(
		spray.boilerplate.BoilerplatePlugin
	)
	.dependsOn(
		api
	)

lazy val cluster = project.in(file("cluster"))
  //.enablePlugins(MultiJvmPlugin)
  //.configs(MultiJvm)
  .settings(settings)
  .settings(
		name := "hyperion-cluster"
		, libraryDependencies ++= Seq(
			lib.akkaCluster
			, lib.akkaClusterTools
			, lib.akkaClusterSharding

			, lib.multiNode
			, lib.levelDB
		)
	)
  .dependsOn(
		core
	)


lazy val mongo = project.in(file("cli/mongo"))
  .settings(settings)
  .settings(
		name := "hyperion-cli-mongo"
		, libraryDependencies ++= Seq(
			lib.mongo
		)
	)
  .dependsOn(
		core
	)


lazy val connectionApi = project.in(file("connection/api"))
  .settings(settings)
  .settings(
		name := "hyperion-connection-api"
	)
  .dependsOn(
		core
	)

lazy val connectionJDBC = project.in(file("connection/jdbc/api"))
  .settings(settings)
  .settings(
		name := "hyperion-connection-jdbc-api"
		, libraryDependencies ++= Seq(
			lib.hikari
		)
	)
  .dependsOn(
		connectionApi
	)

lazy val connectionDerby = project.in(file("connection/jdbc/derby"))
  .settings(settings)
  .settings(
		name := "hyperion-connection-jdbc-derby"
		, libraryDependencies ++= Seq (

			lib.derby
			, lib.derbyCli
			, lib.derbyTools
		)
	)
  .dependsOn(
		connectionJDBC
	)

lazy val lib = new {
	object Version {
		val scala = "2.12.8"

		val akka = "2.5.21"
		val akkaHttp = "10.1.7"

		val logback = "1.2.3"

		val argonaut = "6.2.2"
		val hseebergerArgonaut = "1.25.2"

		val time = "0.1.1"

		val levelDB = "1.8"

		val mongo = "2.6.0"

		val hikari = "3.3.1"
		val derby = "10.13.1.1"
	}

	val scalaReflect = "org.scala-lang" % "scala-reflect" % Version.scala

	val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
	val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
	val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Version.akka
	val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Version.akka
	val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Version.akka
	val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
	val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Version.akka
	val akkaHttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
	val akkaSLF4J = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
	val logback = "ch.qos.logback" % "logback-classic" % Version.logback

	val argonaut = "io.argonaut" %% "argonaut" % Version.argonaut
	val hseebergerArgonaut = "de.heikoseeberger" %% "akka-http-argonaut" % Version.hseebergerArgonaut excludeAll(ExclusionRule(organization = "com.typesafe.akka"))

	val time = "de.ioswarm" %% "scala-time" % Version.time
	val timeArgonaut = "de.ioswarm" %% "scala-time-argonaut" % Version.time


	val mongo = "org.mongodb.scala" %% "mongo-scala-driver" % Version.mongo


	/* JDBC Connection Pool */
	val hikari = "com.zaxxer" % "HikariCP" % Version.hikari



	// protobuf
	val runtimeLib = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"

	val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.akka % Test
	val multiNode = "com.typesafe.akka" %% "akka-multi-node-testkit" % Version.akka % Test

	val levelDB = "org.fusesource.leveldbjni" % "leveldbjni-all" % Version.levelDB % Test

	/* Derby */
	val derby = "org.apache.derby" % "derby" % Version.derby % Test
	val derbyCli = "org.apache.derby" % "derbyclient" % Version.derby % Test
	val derbyTools = "org.apache.derby" % "derbytools" % Version.derby % Test
}

