import com.typesafe.sbt.GitPlugin
import org.scalafmt.sbt.ScalaFmtPlugin
import sbt._
import sbt.plugins.JvmPlugin
import sbt.Keys._

object Build extends AutoPlugin {

  override def requires = JvmPlugin && GitPlugin && ScalaFmtPlugin

  override def trigger = allRequirements

  override def projectSettings =
    ScalaFmtPlugin.autoImport.reformatOnCompileSettings ++
    Vector(
      // Core settings
      organization := "com.example", 
      scalaVersion := Version.Scala,
      crossScalaVersions := Vector(scalaVersion.value),
      scalacOptions ++= Vector(
        "-unchecked",
        "-deprecation",
        "-language:_",
        "-target:jvm-1.8",
        "-encoding", "UTF-8"
      ),
      unmanagedSourceDirectories.in(Compile) := Vector(scalaSource.in(Compile).value),
      unmanagedSourceDirectories.in(Test) := Vector(scalaSource.in(Test).value),

      // scalafmt settings
      ScalaFmtPlugin.autoImport.scalafmtConfig := Some(baseDirectory.in(ThisBuild).value / ".scalafmt"),
      // Git settings
      GitPlugin.autoImport.git.useGitDescribe := true
    )
}
