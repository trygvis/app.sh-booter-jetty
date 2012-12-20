package io.trygvis.appsh.booter.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JettyWebServer {
    private File basedir = new File("").getAbsoluteFile();
    private File tmp, extraClasspath;
    private int httpPort = 8080;

    List<Context> contexts = new ArrayList<Context>();

    public void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    public void setHttpPort(int port) {
        this.httpPort = port;
    }

    public Context addContext(String contextPath, File webapp) throws Exception {
        Context context = new Context();
        context.setContextPath(contextPath);
        context.setWebapp(webapp);
        contexts.add(context);
        return context;
    }

    public void run() throws Exception {
        tmp = new File(basedir, "tmp");

        if (!tmp.isDirectory() && !tmp.mkdirs()) {
            throw new IOException("Could not create temp directory: " + tmp);
        }

        extraClasspath = new File(basedir, "etc");

        if (!extraClasspath.isDirectory()) {
            extraClasspath = null;
        }

        System.setProperty("org.mortbay.log.class", StdErrLog.class.getName());

        Server server = new Server();
        if (httpPort != 0) {
            SelectChannelConnector connector = new SelectChannelConnector();
            connector.setPort(httpPort);

            // http://docs.codehaus.org/display/JETTY/Configuring+Connectors - Look for "forwarded"
            // http://docs.codehaus.org/display/JETTY/Configuring+mod_proxy
            // http://httpd.apache.org/docs/2.2/mod/mod_proxy.html#x-headers
            connector.setForwarded(true);
            server.addConnector(connector);
        }

        ContextHandlerCollection handler = new ContextHandlerCollection();
        server.setHandler(handler);
        for (Context context : contexts) {
            handler.addHandler(context.toJetty());
        }

        server.start();
        server.join();
    }

    public class Context {
        private File webapp;
        private String contextPath;

        public void setWebapp(File webapp) throws IOException {
            if (!webapp.exists()) {
                throw new IOException("File has to exist: " + webapp);
            }
            this.webapp = webapp;
        }

        public void setContextPath(String contextPath) {
            if (!contextPath.startsWith("/")) {
                throw new RuntimeException("The context path has to start with '/'.");
            }

            this.contextPath = contextPath;
        }

        public ContextHandler toJetty() {
            WebAppContext context = new WebAppContext();
            context.setContextPath(this.contextPath);
            context.setWar(webapp.getAbsolutePath());
            if (extraClasspath != null) {
                context.setExtraClasspath(extraClasspath.getAbsolutePath());
            }

            context.setExtractWAR(true);
            // TODO: Should the temp directory be cleaned out before starting?
            String dir = contextPath.substring(1);
            if (dir.length() == 0) {
                dir = "ROOT";
            }
            context.setTempDirectory(new File(tmp, dir));
            return context;
        }
    }
}
