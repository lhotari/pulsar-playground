package com.github.lhotari.pulsar.playground;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Callback;

/**
 * A simple proxy that replaces the localhost Pulsar broker's target port in the response content.
 * This is useful for routing requests via Toxiproxy or another failure proxy
 * to simulate network failures for broker connections.
 * The Pulsar client should use the http url of this proxy in the serviceUrl to achieve this.
 */
public class PulsarLookupProxy {

    private final Server server;

    @SneakyThrows
    public PulsarLookupProxy(int bindPort, int brokerHttpPort, int brokerPulsarPort, int failureProxyPulsarPort) {
        server = new Server(bindPort);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder proxyServlet =
                new ServletHolder(new ReplacingProxyServlet(brokerHttpPort, "localhost:" + brokerPulsarPort,
                        "localhost:" + failureProxyPulsarPort));
        context.addServlet(proxyServlet, "/*");

        server.start();
    }

    public int getBindPort() {
        return Arrays.stream(server.getConnectors()).filter(ServerConnector.class::isInstance)
                .map(ServerConnector.class::cast).findFirst().map(connector -> connector.getLocalPort()).orElseThrow();
    }

    @SneakyThrows
    public void stop() {
        server.stop();
    }

    public static class ReplacingProxyServlet extends ProxyServlet {
        private final int brokerHttpPort;
        private final String search;
        private final String replacement;

        public ReplacingProxyServlet(int brokerHttpPort, String search, String replacement) {
            this.brokerHttpPort = brokerHttpPort;
            this.search = search;
            this.replacement = replacement;
        }

        @Override
        protected String rewriteTarget(HttpServletRequest clientRequest) {
            StringBuilder url = new StringBuilder();
            url.append("http://localhost:" + brokerHttpPort);
            url.append(clientRequest.getRequestURI());
            String query = clientRequest.getQueryString();
            if (query != null) {
                url.append("?").append(query);
            }
            return url.toString();
        }

        @Override
        protected void onResponseContent(HttpServletRequest request, HttpServletResponse response,
                                         Response proxyResponse, byte[] buffer, int offset, int length,
                                         Callback callback) {
            String contentString = new String(buffer, offset, length, StandardCharsets.UTF_8);
            String replaced = contentString.replace(search, replacement);
            if (replaced.equals(contentString)) {
                super.onResponseContent(request, response, proxyResponse, buffer, offset, length, callback);
            } else {
                byte[] replacedBuffer = replaced.getBytes(StandardCharsets.UTF_8);
                response.setHeader("Content-Length", null);
                super.onResponseContent(request, response, proxyResponse, replacedBuffer, 0, replacedBuffer.length,
                        callback);
            }
        }
    }
}