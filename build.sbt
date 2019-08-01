val artifactory = "https://cognite.jfrog.io/cognite/"
inThisBuild(
  List(
    organization := "com.cognite.fs2-grpc",
    git.useGitDescribe := true,
    //scmInfo := Some(ScmInfo(url("https://github.com/cognitedata/fs2-grpc"), "git@github.com:cognitedata/fs2-grpc.git")),
    resolvers += "libs-release" at artifactory + "libs-release/",
    version := "0.4.15"
  ))

lazy val root = project.in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    skip in publish := true,
    pomExtra in Global := {
        <licenses>
          <license>
            <name>MIT</name>
              <url>https://github.com/fiadliel/fs2-grpc/blob/master/LICENSE</url>
          </license>
        </licenses>
        <developers>
          <developer>
            <id>fiadliel</id>
            <name>Gary Coady</name>
            <url>https://www.lyranthe.org/</url>
          </developer>
        </developers>
    }
  )
  .aggregate(`sbt-java-gen`, `java-runtime`)

lazy val `sbt-java-gen` = project
  .enablePlugins(BuildInfoPlugin)
  .settings(
    sbtPlugin := true,
    publishMavenStyle := true,
    crossSbtVersions := List(sbtVersion.value, "0.13.18"),
    buildInfoPackage := "org.lyranthe.fs2_grpc.buildinfo",
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.23"),
    libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion,
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at artifactory + "libs-snapshot-local/")
      else
        Some("releases"  at artifactory + "libs-release-local/")
    }
  )

lazy val `java-runtime` = project
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := List(scalaVersion.value, "2.11.12", "2.13.0-M5"),
    libraryDependencies ++= List(
      "co.fs2"        %% "fs2-core"         % "1.0.4",
      "org.typelevel" %% "cats-effect"      % "1.3.1",
      "org.typelevel" %% "cats-effect-laws" % "1.3.1" % "test",
      "io.grpc"       % "grpc-core"         % scalapb.compiler.Version.grpcJavaVersion,
      "io.grpc"       % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion % "test",
      "io.monix"      %% "minitest"         % "2.3.2" % "test"
    ),
    mimaPreviousArtifacts := Set(organization.value %% name.value % "0.3.0"),
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.9" cross CrossVersion.binary),
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at artifactory + "libs-snapshot-local/")
      else
        Some("releases"  at artifactory + "libs-release-local/")
    }
  )
