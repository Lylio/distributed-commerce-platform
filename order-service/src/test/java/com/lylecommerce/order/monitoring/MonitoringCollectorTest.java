package com.lylecommerce.order.monitoring;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringCollectorTest {
    @Test void reportsActualHealthyDegradedAndUnavailableChecks() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/up/actuator/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/down/actuator/health", exchange -> {
            byte[] body = "{\"status\":\"DOWN\"}".getBytes(); exchange.sendResponseHeaders(503, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            String root = "http://127.0.0.1:" + server.getAddress().getPort();
            var collector = new MonitoringCollector(mock(MonitoringStore.class), mock(MonitoringEvidenceController.class), new ObjectMapper(), root + "/up", root + "/down", "http://127.0.0.1:1", "127.0.0.1:1");
            collector.checkHealth();
            assertEquals("healthy", collector.health().get(0).state());
            assertEquals("degraded", collector.health().get(1).state());
            assertEquals("unavailable", collector.health().get(2).state());
            assertEquals("unavailable", collector.health().get(3).state());
        } finally { server.stop(0); }
    }
}
