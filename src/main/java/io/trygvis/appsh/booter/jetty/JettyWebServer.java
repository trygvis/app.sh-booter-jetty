package io.trygvis.appsh.booter.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * A web server that's expected to be used from main() methods.
 *
 * The main method is expected to know how to configure the server. It will use System.err and System.exit(-1) on
 * errors.
 */
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

    /**
     * Adds a web application context from within the classpath.
     *
     * To get JSPs to work the app has to be extracted
     *
     * @param contextPath Where to "mount" the application, for example /myapp
     * @param war Where there extracted files are places. Will be cleaned out before starting.
     * @param prefix Where to start the classpath scan, for example "/my-awesome-webapp".
     */
    public Context addClasspathContext(String contextPath, File war, String prefix) throws Exception {
        war = war.getAbsoluteFile();

        // Clean out any old cruft
        if (war.isDirectory()) {
            IO.delete(war);
        }

        if (!war.mkdirs()) {
            System.err.println("Could not create directory: " + war);
            System.exit(-1);
        }

        // Classloaders doesn't like slash prefixed searches.
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }

        Enumeration<URL> enumeration = Main.class.getClassLoader().getResources(prefix);

        // TODO: just check for presence of WEB-INF/web.xml after extraction.
        if (!enumeration.hasMoreElements()) {
            System.err.println("Could not look up classpath resource: '" + prefix + "'.");
            System.exit(-1);
        }

        while (enumeration.hasMoreElements()) {
            URL url = enumeration.nextElement();

            try {
                Resource resource = Resource.newResource(url);

                resource.copyTo(war);
            } catch (IOException e) {
                System.err.println("Unable to extract " + url.toExternalForm() + " to " + war);
            }
        }

        return addContext(contextPath, war);
    }

    /**
     * This should be moved to the main method. JettyWebServer should only be code to set up Jetty.
     */
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
            handler.addHandler(context.toJetty(tmp));
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

            /*
             * By setting useFileMappedBuffer we prevent Jetty from memory-mapping the static resources
             *
             * The default servlet can be configured by setting parameters on the context in addition to the default
             * servlet's init params, saving us from injecting the DefaultServlet programmatically.
             *
             * http://stackoverflow.com/questions/184312/how-to-make-jetty-dynamically-load-static-pages
             */
            context.setInitParameter("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");

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
