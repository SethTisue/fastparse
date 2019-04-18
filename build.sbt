val Constants = _root_.fastparse.Constants
import sbtcrossproject.{crossProject, CrossType}
import sbt.Keys._

shared
noPublish

def macroDependencies(version: String) =
  Seq(
    "org.scala-lang" % "scala-reflect" % version % "provided",
    "org.scala-lang" % "scala-compiler" % version % "provided"
  ) ++
  (if (version startsWith "2.10.")
     Seq(compilerPlugin("org.scalamacros" % s"paradise" % "2.1.0" cross CrossVersion.full),
         "org.scalamacros" %% s"quasiquotes" % "2.1.0")
   else
     Seq())

val shared = Seq(
  libraryDependencies ++= macroDependencies(scalaVersion.value),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "utest" % "0.5.4" % "test",
    "com.lihaoyi" %%% "sourcecode" % "0.1.4"
  ),
  organization := "com.lihaoyi",
  version := Constants.version,
  scalaVersion := Constants.scala212,
  crossScalaVersions := Seq(Constants.scala210, Constants.scala211, Constants.scala212),
  libraryDependencies += "com.lihaoyi" %% "acyclic" % "0.1.5" % "provided",
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.5"),
  autoCompilerPlugins := true,
  testFrameworks += new TestFramework("utest.runner.Framework"),
  publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
  pomExtra :=
    <url>https://github.com/lihaoyi/scala-parser</url>
      <licenses>
        <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/lihaoyi/fastparse.git</url>
        <connection>scm:git://github.com/lihaoyi/fastparse.git</connection>
      </scm>
      <developers>
        <developer>
          <id>lihaoyi</id>
          <name>Li Haoyi</name>
          <url>https://github.com/lihaoyi</url>
        </developer>
      </developers>
)

lazy val noPublish = Seq(
  skip in publish := true
)

lazy val utils = project
  .settings(
    name := "fastparse-utils",
    shared,
    unmanagedSourceDirectories in Compile ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 12 =>
          Seq(baseDirectory.value / ".." / "shared" / "src" / "main" / "scala-2.11")
        case _ =>
          Seq()
      }
    }
  )

lazy val fastparse = project
  .dependsOn(utils)
  .settings(
    shared,
    name := "fastparse",
    sourceGenerators in Compile += Def.task {
      val dir = (sourceManaged in Compile).value 
      val file = dir/"fastparse"/"core"/"SequencerGen.scala"
      // Only go up to 21, because adding the last element makes it 22
      val tuples = (2 to 21).map{ i =>
        val ts = (1 to i) map ("T" + _)
        val chunks = (1 to i) map { n =>
          s"t._$n"
        }
        val tsD = (ts :+ "D").mkString(",")
        s"""
          implicit def Sequencer$i[$tsD]: Sequencer[(${ts.mkString(", ")}), D, ($tsD)] =
            Sequencer0((t, d) => (${chunks.mkString(", ")}, d))
          """
      }
      val output = s"""
          package fastparse.core
          trait SequencerGen[Sequencer[_, _, _]] extends LowestPriSequencer[Sequencer]{
            protected[this] def Sequencer0[A, B, C](f: (A, B) => C): Sequencer[A, B, C]
            ${tuples.mkString("\n")}
          }
          trait LowestPriSequencer[Sequencer[_, _, _]]{
            protected[this] def Sequencer0[A, B, C](f: (A, B) => C): Sequencer[A, B, C]
            implicit def Sequencer1[T1, T2]: Sequencer[T1, T2, (T1, T2)] = Sequencer0{case (t1, t2) => (t1, t2)}
          }
        """.stripMargin
      IO.write(file, output)
      Seq(file)
    }
  )
  // In order to make the midi-parser-test in fastparse/test:run work
  .settings(fork in (Test, run) := true)

lazy val fastparseByte = project
  .dependsOn(fastparse)
  .settings(
    shared,
    name := "fastparse-byte",
    libraryDependencies += "org.scodec" %%% "scodec-bits" % "1.1.5"
  )

lazy val scalaparse = project
  .dependsOn(fastparse)
  .settings(
    shared,
    name := "scalaparse"
  )
  .settings(
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test"
  )

lazy val pythonparse = project
  .dependsOn(fastparse)
  .settings(shared:_*)
  .settings(
    name := "pythonparse"
  )

lazy val cssparse = project
  .dependsOn(fastparse)
  .settings(
    shared,
    name := "cssparse"
  )
  .settings(
    libraryDependencies += "net.sourceforge.cssparser" % "cssparser" % "0.9.18" % "test"
  )

lazy val classparse = project
  .dependsOn(fastparseByte)
  .settings(
    shared,
    name := "classparse"
  )

lazy val perftests = project
  .dependsOn(
    fastparse % "compile->compile;compile->test",
    fastparseByte % "compile->compile;compile->test",
    pythonparse,
    scalaparse,
    cssparse,
    classparse
  )
  .settings(
    shared,
    noPublish,
    name := "perfomance-tests",
    parallelExecution := false
  )
