package org.lyranthe.fs2_grpc.java_runtime.sbt_gen

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import org.lyranthe.fs2_grpc.java_runtime.sbt_gen.Fs2GrpcPlugin.autoImport.scalapbCodeGenerators
import protocbridge.{Artifact, JvmGenerator, ProtocCodeGenerator, Target}
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB
import scalapb.compiler.{FunctionalPrinter, GeneratorException, DescriptorImplicits, ProtobufGenerator}
import scalapb.options.compiler.Scalapb
import scala.collection.JavaConverters._
import org.lyranthe.fs2_grpc.buildinfo.BuildInfo

sealed trait CodeGeneratorOption extends Product with Serializable

class Fs2CodeGenerator(serviceSuffix: String) extends ProtocCodeGenerator {

  def generateServiceFiles(
      file: FileDescriptor,
      di: DescriptorImplicits
  ): Seq[PluginProtos.CodeGeneratorResponse.File] = {
    file.getServices.asScala.map { service =>
      val p = new Fs2GrpcServicePrinter(service, serviceSuffix, di)

      import di.{ServiceDescriptorPimp, FileDescriptorPimp}
      val code = p.printService(FunctionalPrinter()).result()
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setName(file.scalaDirectory + "/" + service.name + s"$serviceSuffix.scala")
      b.setContent(code)
      println(b.getName)
      b.build
    }
  }

  def handleCodeGeneratorRequest(request: PluginProtos.CodeGeneratorRequest): PluginProtos.CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    ProtobufGenerator.parseParameters(request.getParameter) match {
      case Right(params) =>
        try {

          val filesByName: Map[String, FileDescriptor] =
            request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
              case (acc, fp) =>
                val deps = fp.getDependencyList.asScala.map(acc)
                acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
            }

          val implicits = new DescriptorImplicits(params, filesByName.values.toVector)
          val genFiles = request.getFileToGenerateList.asScala.map(filesByName)
          val srvFiles = genFiles.flatMap(generateServiceFiles(_, implicits))
          b.addAllFile(srvFiles.asJava)
        } catch {
          case e: GeneratorException =>
            b.setError(e.message)
        }

      case Left(error) =>
        b.setError(error)
    }

    b.build()
  }

  override def run(req: Array[Byte]): Array[Byte] = {
    println("Running")
    val registry = ExtensionRegistry.newInstance()
    Scalapb.registerAllExtensions(registry)
    val request = CodeGeneratorRequest.parseFrom(req, registry)
    handleCodeGeneratorRequest(request).toByteArray
  }

  override def suggestedDependencies: Seq[Artifact] = Seq(
    Artifact("com.thesamet.scalapb", "scalapb-runtime", scalapb.compiler.Version.scalapbVersion, crossVersion = true)
  )
}

object Fs2Grpc extends AutoPlugin {
  override def requires: Plugins = Fs2GrpcPlugin
  override def trigger: PluginTrigger = NoTrigger

  override def projectSettings: Seq[Def.Setting[_]] = List(
    Compile / PB.targets := scalapbCodeGenerators.value
  )
}

object Fs2GrpcPlugin extends AutoPlugin {
  object autoImport {
    val grpcJavaVersion: String = scalapb.compiler.Version.grpcJavaVersion

    object CodeGeneratorOption {
      case object FlatPackage extends CodeGeneratorOption {
        override def toString = "flat_package"
      }
      case object JavaConversions extends CodeGeneratorOption {
        override def toString: String = "java_conversions"
      }
      case object Grpc extends CodeGeneratorOption {
        override def toString: String = "grpc"
      }
      case object Fs2Grpc extends CodeGeneratorOption {
        override def toString: String = "fs2_grpc"
      }
      case object SingleLineToProtoString extends CodeGeneratorOption {
        override def toString: String = "single_line_to_proto_string"
      }
      case object AsciiFormatToString extends CodeGeneratorOption {
        override def toString: String = "ascii_format_to_string"
      }
    }

    val scalapbCodeGeneratorOptions =
      settingKey[Seq[CodeGeneratorOption]]("Settings for scalapb/fs2-grpc code generation")
    val scalapbProtobufDirectory =
      settingKey[File]("Directory containing protobuf files for scalapb")
    val scalapbCodeGenerators =
      settingKey[Seq[Target]]("Code generators for scalapb")
    val fs2GrpcServiceSuffix =
      settingKey[String](
        "Suffix used for generated service, e.g. service `Foo` with suffix `Fs2Grpc` results in `FooFs2Grpc`"
      )

  }
  import autoImport._

  override def requires = sbtprotoc.ProtocPlugin && JvmPlugin
  override def trigger = NoTrigger

  def convertOptionsToScalapbGen(options: Set[CodeGeneratorOption]): (JvmGenerator, Seq[String]) = {
    scalapb.gen(
      flatPackage = options(CodeGeneratorOption.FlatPackage),
      javaConversions = options(CodeGeneratorOption.JavaConversions),
      grpc = options(CodeGeneratorOption.Grpc),
      singleLineToProtoString = options(CodeGeneratorOption.SingleLineToProtoString),
      asciiFormatToString = options(CodeGeneratorOption.AsciiFormatToString)
    )
  }

  override def projectSettings: Seq[Def.Setting[_]] = List(
    fs2GrpcServiceSuffix := "Fs2Grpc",
    scalapbProtobufDirectory := (sourceManaged in Compile).value / "scalapb",
    scalapbCodeGenerators := {
      Target(
        convertOptionsToScalapbGen(scalapbCodeGeneratorOptions.value.toSet),
        (sourceManaged in Compile).value / "scalapb"
      ) ::
        Option(
          Target(
            (
              JvmGenerator("scala-fs2-grpc", new Fs2CodeGenerator(fs2GrpcServiceSuffix.value)),
              scalapbCodeGeneratorOptions.value.filterNot(_ == CodeGeneratorOption.Fs2Grpc).map(_.toString)
            ),
            (sourceManaged in Compile).value / "fs2-grpc"
          )
        ).filter(_ => scalapbCodeGeneratorOptions.value.contains(CodeGeneratorOption.Fs2Grpc)).toList
    },
    scalapbCodeGeneratorOptions := Seq(CodeGeneratorOption.Grpc, CodeGeneratorOption.Fs2Grpc),
    libraryDependencies ++= List(
      "io.grpc" % "grpc-core" % BuildInfo.grpcVersion,
      "io.grpc" % "grpc-stub" % BuildInfo.grpcVersion,
      "io.grpc" % "grpc-protobuf" % BuildInfo.grpcVersion,
      "com.cognite.fs2-grpc" %% "java-runtime" % BuildInfo.version,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    )
  )
}
