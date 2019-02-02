name := "gitbucket-ldapserver-poc-plugin"
organization := "io.github.gitbucket"
version := "1.0.0"
scalaVersion := "2.12.8"
gitbucketVersion := "4.30.1"

libraryDependencies ++= Seq(
  "org.apache.directory.server" % "apacheds-all" % "2.0.0-M24"
)
