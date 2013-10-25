Sakai Archiver

This is an external Sakai utility that is intended to allow
course sites to be exported as HTML.

It is a Maven project.  To get started, clone the repro and 
then use mvn clean install to get the dependancies.

To run, edit the sakai-archiver.properties to match your
site requirements.

Use:  mvn exec:java -Dsite=[course site] -Duser=[user id] -Dpassword=[password]

NOTE: 

An optional -Doption=[full path to properties file] can be added to override the
default properties file.
