# Change Log

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
