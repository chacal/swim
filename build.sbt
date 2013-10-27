scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "io.spray" % "spray-can" % "1.2-RC1"

libraryDependencies += "io.spray" % "spray-routing" % "1.2-RC1"

libraryDependencies += "io.spray" % "spray-client" % "1.2-RC1"

libraryDependencies += "io.spray" %% "spray-json" % "1.2.5"