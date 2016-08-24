lazy val sargon = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin, GitVersioning)

libraryDependencies ++= Vector(
  Library.akkaActor,
  Library.akkaAgent,
  Library.akkaStream,
  Library.akkaSlf4j,
  Library.logback,
  Library.scalaLogging,
  Library.pprint,

  Library.akkaStreamTestkit % "test",
  Library.akkaTestkit % "test",
  Library.scalaTest % "test"
)

initialCommands := """|import com.example.sargon._
                      |""".stripMargin
