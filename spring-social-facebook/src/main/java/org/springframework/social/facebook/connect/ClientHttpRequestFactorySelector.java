package org.springframework.social.facebook.connect;

import org.apache.http.HttpHost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.ClassUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ClientHttpRequestFactorySelector {

    public static ClientHttpRequestFactory foo() {
        return new HttpComponentsClientHttpRequestFactory();
    }

    public static ClientHttpRequestFactory getRequestFactory() {
        Properties properties = System.getProperties();
        String proxyHost = properties.getProperty("http.proxyHost");
        int proxyPort = properties.containsKey("http.proxyPort") ? Integer.valueOf(properties.getProperty("http.proxyPort")) : 80;
        if (HTTP_COMPONENTS_AVAILABLE) {
            return HttpComponentsClientRequestFactoryCreator.createRequestFactory(proxyHost, proxyPort);
        } else {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            if (proxyHost != null) {
                requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            }
            return requestFactory;
        }
    }

    /**
     * Decorates a request factory to buffer responses so that the responses may be repeatedly read.
     *
     * @param requestFactory the request factory to be decorated for buffering
     * @return a buffering request factory
     */
    public static ClientHttpRequestFactory bufferRequests(ClientHttpRequestFactory requestFactory) {
        return new BufferingClientHttpRequestFactory(requestFactory);
    }

    private static final boolean HTTP_COMPONENTS_AVAILABLE = ClassUtils.isPresent("org.apache.http.client.HttpClient", ClientHttpRequestFactory.class.getClassLoader());

    public static class HttpComponentsClientRequestFactoryCreator {

        public static ClientHttpRequestFactory createRequestFactory(String proxyHost, int proxyPort) {

            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory() {
                @Override
                protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
                    HttpClientContext context = new HttpClientContext();
                    context.setAttribute("http.protocol.expect-continue", false);
                    return context;
                }
            };


            if (proxyHost != null) {
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);

                PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
                cm.setValidateAfterInactivity(30 * 1000);
                // Increase max total connection to 200
                cm.setMaxTotal(100);
                // Increase default max connection per route to 25
                cm.setDefaultMaxPerRoute(25);
                // Increase max connections for localhost:80 to 50
                //HttpHost localhost = new HttpHost("locahost", 80);
                //cm.setMaxPerRoute(new HttpRoute(localhost), 50);

                IdleConnectionMonitorThread idleConnectionMonitorThread = new IdleConnectionMonitorThread(cm);



                CloseableHttpClient httpClient = HttpClients.custom()
                        .setProxy(proxy)
                        .setConnectionManager(cm)
                        .build();
                requestFactory.setHttpClient(httpClient);

                idleConnectionMonitorThread.start();
            }

            return requestFactory;

        }
    }

    public static class IdleConnectionMonitorThread extends Thread {

        private final HttpClientConnectionManager connMgr;
        private volatile boolean shutdown;

        public IdleConnectionMonitorThread(HttpClientConnectionManager connMgr) {
            super();
            this.connMgr = connMgr;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);
                        // Close expired connections
                        connMgr.closeExpiredConnections();
                        // Optionally, close connections
                        // that have been idle longer than 30 sec
                        connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                // terminate
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }

    }

}
