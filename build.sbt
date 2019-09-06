val artifactory = "https://cognite.jfrog.io/cognite/"
import Dependencies._

inThisBuild(
  List(
    scalaVersion := "2.12.9",
    organization := "com.cognite.fs2-grpc",
    git.useGitDescribe := true,
    //scmInfo := Some(ScmInfo(url("https://github.com/cognitedata/fs2-grpc"), "git@github.com:cognitedata/fs2-grpc.git")),
    resolvers += "libs-release" at artifactory + "libs-release/",
    version := "0.4.16"
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
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at artifactory + "libs-snapshot-local/")
      else
        Some("releases"  at artifactory + "libs-release-local/")
    },
    addSbtPlugin(sbtProtoc),
    libraryDependencies += scalaPbCompiler
  )

lazy val `java-runtime` = project
  .settings(
    scalaVersion := "2.13.0",
    crossScalaVersions := List(scalaVersion.value, "2.12.9", "2.11.12"),
    publishTo := sonatypePublishTo.value,
    libraryDependencies ++= List(fs2, catsEffect, grpcCore) ++ List(grpcNetty, catsEffectLaws, minitest).map(_  % Test),
    mimaPreviousArtifacts := Set(organization.value %% name.value % "0.3.0"),
    Test / parallelExecution := false,
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at artifactory + "libs-snapshot-local/")
      else
        Some("releases"  at artifactory + "libs-release-local/")
    },
    addCompilerPlugin(kindProjector)
  )
