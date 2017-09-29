package org.jenkinsci.plugins.ghprb;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

import org.kohsuke.github.HttpConnector;


public class HttpConnectorWithProxyAndCache implements HttpConnector {
    private static final Logger LOGGER = Logger.getLogger(HttpConnectorWithProxyAndCache.class.getName());
        
    public HttpURLConnection connect(URL url) throws IOException {
        OkHttpClient client = new OkHttpClient();
        if (Jenkins.getInstance().proxy != null) {
            client.setProxy(Jenkins.getInstance().proxy.createProxy(url.getHost()));
        }
        
        // Set up a 100MB cache
        Path cacheDir = new File(Jenkins.getInstance().getRootDir(), "Ghprb.cache").toPath();
        Cache cache = new Cache(cacheDir.toFile(), 10 * 1024 * 1024);
        client.setCache(cache);
        
        // Set default timeouts in case there are none
        if (client.getConnectTimeout() == 0) {
            client.setConnectTimeout(10000, TimeUnit.MILLISECONDS);
        }
        if (client.getReadTimeout() == 0) {
            client.setReadTimeout(10000, TimeUnit.MILLISECONDS);
        }
        
        LOGGER.log(Level.INFO, "HttpConnectorWithProxyAndCache: Creating connection with cache at {0} for url {1}",
                new Object[] {cacheDir.toString(), url.toString()});
        
        OkUrlFactory urlFactory = new OkUrlFactory(client);
        return urlFactory.open(url);
    }
}
