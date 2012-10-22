package io.trygvis.appsh.booter.jetty8;

import org.eclipse.jetty.util.IO;

import java.io.*;
import java.util.Map;
import java.util.Properties;

import static java.lang.Integer.parseInt;

public class Main {

    /**
     * The file that this booter will read it's configuration from.
     */
    public static final String PROPERTIES_FILE = "etc/booter.properties";

    public static void main(String[] args) throws Exception {
        File basedir = new File(System.getProperty("basedir", new File("").getAbsolutePath()));

        File booterPropertiesFile = new File(basedir, PROPERTIES_FILE);

        Properties properties = new Properties();
        InputStream is = null;
        try {
            is = new FileInputStream(booterPropertiesFile);
            properties.load(new InputStreamReader(is, "utf-8"));
        } catch (FileNotFoundException e) {
            System.err.println("Can't read: " + booterPropertiesFile);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error reading: " + booterPropertiesFile);
            System.exit(1);
        } finally {
            IO.close(is);
        }

        setStreams(basedir, properties);

        JettyWebServer server;
        try {
            server = new JettyWebServer();
            server.setBasedir(basedir);

            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue().toString();

                if (key.startsWith("context.")) {
                    server.addContext(key.substring(8), new File(basedir, value));
                }
            }

            String httpPort = properties.getProperty("httpPort", System.getenv("httpPort"));
            if (httpPort != null) {
                server.setHttpPort(parseInt(httpPort));
            }
        } catch (Exception e) {
            System.err.println("Error while configuring Jetty.");
            e.printStackTrace();
            System.exit(1);
            return;
        }

        try {
            server.run();
        } catch (Exception e) {
            System.err.println("Error while starting Jetty.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void setStreams(File basedir, Properties properties) throws IOException {
        String logS = properties.getProperty("log");

        if(logS == null) {
            return;
        }

        File log = new File(basedir, logS);

        if(!log.getParentFile().isDirectory()) {
            if(!log.getParentFile().mkdirs()) {
                System.err.println("Unable to create directory: " + log.getAbsolutePath());
                System.exit(1);
            }
        }

        PrintStream writer = new PrintStream(new FileOutputStream(log));

        System.setOut(writer);
        System.setErr(writer);
    }
}
