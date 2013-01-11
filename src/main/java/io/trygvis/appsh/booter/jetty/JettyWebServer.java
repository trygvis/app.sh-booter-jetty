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
    private int httpPort = 8080;

    List<Context> contexts = new ArrayList<Context>();

    public void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    public void setHttpPort(int port) {
        this.httpPort = port;
    }

    public Context addContext(Context context) throws Exception {
        contexts.add(context);
        return context;
    }

    /**
     * This should be moved to the main method. JettyWebServer should only be code to set up Jetty.
     */
    public void run() throws Exception {
        File tmp = new File(basedir, "tmp/jetty-booter");

        if (!tmp.isDirectory() && !tmp.mkdirs()) {
            throw new IOException("Could not create temp directory: " + tmp);
        }

        File extraClasspath = new File(basedir, "etc");

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

        ServerSettings settings = new ServerSettings(tmp, extraClasspath != null ? extraClasspath.getAbsolutePath() : null);

        for (Context context : contexts) {
            handler.addHandler(context.toJetty(settings));
        }

        server.start();
        server.join();
    }

    public static class ServerSettings {
        public final File tmp;
        public final String extraClasspath;

        public ServerSettings(File tmp, String extraClasspath) {
            this.tmp = tmp;
            this.extraClasspath = extraClasspath;
        }
    }

    private static abstract class Context {
        public final String contextPath;

        protected Context(String contextPath) {
            if (!contextPath.startsWith("/")) {
                throw new RuntimeException("The context path has to start with '/'.");
            }

            this.contextPath = contextPath;
        }

        public String getContextPath() {
            return contextPath;
        }

        protected abstract ContextHandler toJetty(ServerSettings settings) throws IOException;
    }

    public static class WarContext extends Context {
        private final File webapp;

        public WarContext(String contextPath, File webapp) throws IOException {
            super(contextPath);
            this.webapp = webapp;

            if (!webapp.exists()) {
                throw new IOException("File has to exist: " + webapp);
            }
        }

        protected ContextHandler toJetty(ServerSettings settings) {
            return warContext(settings, contextPath, webapp);
        }

        public static ContextHandler warContext(ServerSettings settings, String contextPath, File webapp) {
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

            context.setContextPath(contextPath);
            context.setWar(webapp.getAbsolutePath());
            if (settings.extraClasspath != null) {
                context.setExtraClasspath(settings.extraClasspath);
            }

            context.setExtractWAR(true);
            // TODO: Should the temp directory be cleaned out before starting?
            String dir = contextPath.substring(1);
            if (dir.length() == 0) {
                dir = "ROOT-tmp";
            }
            context.setTempDirectory(new File(settings.tmp, dir));
            return context;
        }
    }

    /**
     * Adds a web application context from within the classpath.
     *
     * To get JSPs to work the app has to be extracted
     */
    public static class ClasspathContext extends Context {
        private final String prefix;

        /**
         * @param contextPath Where to "mount" the application, for example /my-app
         * @param prefix Where to start the classpath scan, for example "/my-awesome-webapp".
         */
        public ClasspathContext(String contextPath, String prefix) {
            super(contextPath);
            this.prefix = prefix;
        }

        protected ContextHandler toJetty(ServerSettings settings) throws IOException {
            String prefix = this.prefix;

            String tmpName;
            if (contextPath.length() == 1) {
                tmpName = "ROOT-web";
            } else {
                tmpName = contextPath.substring(1).replace('/', '_');
            }

            File war = new File(settings.tmp, tmpName);
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
                    System.err.println("Unable to extract " + url.toExternalForm() + " to " + war + ": " + e.getMessage());
                    System.exit(-1);
                }
            }

            return WarContext.warContext(settings, contextPath, war);
        }
    }
}
