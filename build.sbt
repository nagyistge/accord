import com.typesafe.sbt.pgp.PgpKeys._
import Helpers._

lazy val publishSettings = Seq(
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if ( version.value.trim.endsWith( "SNAPSHOT" ) )
      Some( "snapshots" at nexus + "content/repositories/snapshots" )
    else
      Some( "releases" at nexus + "service/local/staging/deploy/maven2" )
  },
  publishMavenStyle := true,
  credentials in Scaladex += Credentials( Path.userHome / ".ivy2" / ".scaladex.credentials" ),
  pomExtra in ThisBuild :=
    <scm>
      <url>git@github.com:wix/accord.git</url>
      <connection>scm:git@github.com:wix/accord.git</connection>
    </scm>
    <developers>
      <developer>
        <id>Holograph</id>
        <name>Tomer Gabel</name>
        <url>http://www.tomergabel.com</url>
      </developer>
    </developers>
)

lazy val releaseSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := publishSigned.value
)

def noFatalWarningsOn( task: sbt.TaskKey[_] = compile, configuration: sbt.Configuration = Compile ) =
  task match {
    case `compile` =>
      scalacOptions in configuration ~= { _ filterNot { _ == "-Xfatal-warnings" } }

    case _ =>
      scalacOptions in ( configuration, task ) :=
        ( scalacOptions in ( Compile, compile ) ).value filterNot { _ == "-Xfatal-warnings" }
  }

// Necessary to work around scala/scala-dev#275 (see wix/accord#84)
def providedScalaCompiler =
  libraryDependencies <+= scalaVersion { "org.scala-lang" % "scala-compiler" % _ % "provided" }

def limitPackageSize( allowedSizeInKB: Int ) =
  packageBin in Compile := {
    val jar = ( packageBin in Compile ).value
    val sizeKB = jar.length() / 1024
    if ( sizeKB > allowedSizeInKB )
      error( s"Resulting package $jar (size=${sizeKB}KB) is larger than the allowed limit of ${allowedSizeInKB}KB" )
    jar
  }

lazy val compileOptions = Seq(
  scalaVersion := "2.11.1",
  crossScalaVersions := ( Helpers.javaVersion match {
    case v if v >= 1.8 => Seq( "2.11.1", "2.12.0" )
    case _             => Seq( "2.11.1" )
  } ),
  scalacOptions ++= Seq(
    "-language:reflectiveCalls",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings"
  ),
  noFatalWarningsOn( task = doc )      // Warnings aren't considered fatal on document generation
) ++ providedScalaCompiler

lazy val baseSettings =
  publishSettings ++
  releaseSettings ++
  compileOptions ++
  sbtdoge.CrossPerProjectPlugin.projectSettings ++
  Seq(
    organization := "com.wix",
    homepage := Some( url( "https://github.com/wix/accord" ) ),
    licenses := Seq( "Apache-2.0" -> url( "http://www.opensource.org/licenses/Apache-2.0" ) )
  )

lazy val noPublish = Seq( publish := {}, publishLocal := {}, publishArtifact := false )

// Projects --

lazy val api =
  crossProject
    .crossType( CrossType.Pure )
    .in( file( "api" ) )
    .settings( Seq(
      name := "accord-api",
      description :=
        "Accord is a validation library written in and for Scala. Its chief aim is to provide a composable, " +
        "dead-simple and self-contained story for defining validation rules and executing them on object " +
        "instances. Feedback, bug reports and improvements are welcome!"
    ) ++ baseSettings :_* )
  .jsSettings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
    limitPackageSize( 150 )
  )
  .jvmSettings(
    libraryDependencies <+= scalaVersion {
      case v if v startsWith "2.12" => "org.scalatest" %% "scalatest" % "3.0.0" % "test"
      case _ => "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    },
    limitPackageSize( 90 )
  )

lazy val apiJVM = api.jvm
lazy val apiJS = api.js

lazy val scalatest =
  crossProject
    .crossType( CrossType.Pure )
    .in( file( "scalatest" ) )
    .dependsOn( api )
    .settings( baseSettings ++ Seq(
      name := "accord-scalatest",
      description := "ScalaTest matchers for the Accord validation library",
      noFatalWarningsOn( configuration = Test )
    ) :_* )
  .jsSettings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0",
    limitPackageSize( 100 )
  )
  .jvmSettings(
    libraryDependencies <+= scalaVersion {
      case v if v startsWith "2.12" => "org.scalatest" %% "scalatest" % "3.0.0"
      case _ => "org.scalatest" %% "scalatest" % "2.2.6"
    },
    limitPackageSize( 60 )
  )
lazy val scalatestJVM = scalatest.jvm
lazy val scalatestJS = scalatest.js

lazy val specs2 =
  Project(
    id = "specs2",
    base = file( "specs2" ),
    settings = baseSettings ++ Seq(
      name := "accord-specs2",
      libraryDependencies <+= scalaVersion {
        case v if v startsWith "2.12" => "org.specs2" %% "specs2-core" % "3.8.6"
        case _ => "org.specs2" %% "specs2-core" % "3.6.5"
      },
      noFatalWarningsOn( compile, Test ),
      limitPackageSize( 80 )
    )
  ).dependsOn( apiJVM )

lazy val core =
  crossProject
    .crossType( CrossType.Pure )
    .in( file( "core" ) )
    .dependsOn( api, scalatest % "test->compile" )
    .settings( Seq(
      name := "accord-core",

      libraryDependencies += "org.scalamacros" %% "resetallattrs" % "1.0.0",
      libraryDependencies <+= scalaVersion( "org.scala-lang" % "scala-reflect" % _ % "provided" ),

      description :=
        "Accord is a validation library written in and for Scala. Its chief aim is to provide a composable, " +
        "dead-simple and self-contained story for defining validation rules and executing them on object " +
        "instances. Feedback, bug reports and improvements are welcome!"
    ) ++ baseSettings :_* )
    .jvmSettings( limitPackageSize( 400 ) )
    .jsSettings( limitPackageSize( 700 ) )
lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val java8 =
  crossProject
    .crossType( CrossType.Pure )
    .in( file( "java8" ) )
    .dependsOn( api, core, scalatest % "test->compile" )
    .settings( Seq(
      name := "accord-java8",
      description := "Adds native Accord combinators for Java 8 features",
      limitPackageSize( 30 )
    ) ++ baseSettings :_* )
    .jsSettings(
      // This library is still not complete (e.g. LocalDateTime isn't implemented); Scala.js support
      // for this module is consequently currently disabled.
      libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % "0.2.0"
    )

lazy val java8JVM = java8.jvm
//lazy val java8JS = java8.js     // Disabled until scalajs-java-time comes along. See comment above

lazy val joda =
  Project(
    id = "joda",
    base = file( "joda" ),
    settings = baseSettings ++ Seq(
      name := "accord-joda",
      libraryDependencies ++= Seq(
        "joda-time" % "joda-time" % "2.9.7",
        "org.joda" % "joda-convert" % "1.8.1"  // Required for rendering constraints
      ),
      description := "Adds native Accord combinators for Joda-Time",
      limitPackageSize( 25 )
    ) )
  .dependsOn( apiJVM, coreJVM, scalatestJVM % "test->compile" )

lazy val spring3 =
  Project(
    id = "spring3",
    base = file ( "spring3" ),
    settings = baseSettings ++ Seq( limitPackageSize( 25 ) )
  )
  .dependsOn( apiJVM, scalatestJVM % "test->compile", coreJVM % "test->compile" )

lazy val examples =
  Project(
    id = "examples",
    base = file( "examples" ),
    settings = baseSettings ++ noPublish ++ Seq(
      name := "accord-examples",
      description := "Sample projects for the Accord validation library.",
      noFatalWarningsOn( configuration = Compile )
    ) )
  .dependsOn( apiJVM, coreJVM, scalatestJVM % "test->compile", specs2 % "test->compile", spring3 )


// Root --

lazy val root =
  Project(
    id = "root",
    base = file( "." ),
    settings = baseSettings ++ noPublish
  )
  .aggregate(
    apiJVM, apiJS, coreJVM, coreJS,                 // Core modules
    scalatestJVM, scalatestJS, specs2,              // Testing support
    spring3, joda,                                  // Optional modules
    examples                                        // Extras
  )
  .whenJavaVersion( _ >= 1.8 ) {
    _.aggregate( java8JVM/*, java8JS*/ )            // Modules that explicitly require Java 8
  }
