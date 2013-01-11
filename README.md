# Usage

This Jetty-based WAR/web application booter is configured through a properties files and environment properties. It
depends on a specific Jetty version.

## Changing the version of Jetty used

Feel free to use Maven's dependency management to override the version that this plugin depend on. Note that it has
some sets of dependencies. The major one is Jetty itself. This version is reflected in the booter's version itself. The
rest of the dependencies are based on what the Jetty version itself uses.

If you change the Jetty version you should probably download the Jetty distribution for the new version and make sure
that the versions of the artifacts that the booter depend on match.

# Change Log

## 8.1.8.v20121106+2

### Support for running web apps from inside a JAR file

Example `booter.properties`:

    context./=classpath:/webapp

Note that because of the current JSP implementation the webapp has to be extracted for the JSP compiler to be able to
find the files. It also requires a temporary area to write the class files.

## 8.1.8.v20121106+1 - 2012-12-20

Upgrading to 8.1.8.

Setting the "forwarded" flag on the connector to true so that it'll use any info from mod_proxy when the app requests
the remote address.

* <http://docs.codehaus.org/display/JETTY/Configuring+Connectors> - Look for "forwarded"
* <http://docs.codehaus.org/display/JETTY/Configuring+mod_proxy>
* <http://httpd.apache.org/docs/2.2/mod/mod_proxy.html#x-headers>


## 8.1.7.v20120910+1 - 2012-10-13

Initial release

# TODO

* Make sure the connector useful for websockets is used by default.
* Make sure the file hack for windows is used by default.
* Make a proper DSL :)
