Sakai Archiver

This is an external Sakai utility that is intended to allow
course sites to be exported as HTML.

It is a Maven project.  To get started, clone the repro and 
then use mvn clean install to get the dependencies.

To configure this, edit the sakai-archiver.properties to match your
site requirements.  In particular, you will need to have the following 
properties set correctly for your site/system:

# This is the location that all Archives will be created in.
archive.dir.base = /tmp/SakaiArchiver/

# This is the Sakai host to access courses from.
sakai.base.url = https://sakainightly.cc.columbia.edu/portal/site/

To run using maven use:

mvn exec:java -Dsite=[course site] -Dcookie=[cookie id]

Where:

Course site is the Sakai course site from the url, e.g. ADMNT2012_01_2012_02.
Cookie is the JSESSIONID copied from your browser (use Firebug in Firefox).

NOTE: 

An optional -Doption=[full path to properties file] can be added to override the
default properties file.

To create an executable jar with all dependencies, use the maven goal:

assembly:single

This jar can be run by:

java -jar [jarfile] [course site] [cookie id] [Optional properties file]



