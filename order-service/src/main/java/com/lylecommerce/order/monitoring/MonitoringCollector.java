package com.lylecommerce.order.monitoring;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

@Component
public class MonitoringCollector {
    public record Health(String service, String state, String detail, Instant checkedAt) {}
    private final MonitoringStore store;
    private final MonitoringEvidenceController local;
    private final ObjectMapper mapper;
    private final Map<String, String> urls;
    private final String bootstrap;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private volatile List<Health> health = List.of();
    // Cache only successfully stored evidence; publication has a separate key from insertion.
    private final Set<String> known = new HashSet<>();
    public MonitoringCollector(MonitoringStore store, MonitoringEvidenceController local, ObjectMapper mapper,
            @Value("${monitoring.order-url:http://localhost:8081}") String order,
            @Value("${monitoring.inventory-url:http://localhost:8082}") String inventory,
            @Value("${monitoring.payment-url:http://localhost:8083}") String payment,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        this.store = store; this.local = local; this.mapper = mapper; this.bootstrap = bootstrap;
        urls = new LinkedHashMap<>(); urls.put("order-service", order); urls.put("inventory-service", inventory); urls.put("payment-service", payment);
    }
    public List<Health> health() { return health; }
    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Scheduled(fixedDelay = 3000)
    public void collect() {
        for (var service : urls.entrySet()) {
            try {
                List<MonitoringEvidenceController.Evidence> evidence;
                if (service.getKey().equals("order-service")) evidence = local.evidence();
                else {
                    var response = get(service.getValue() + "/internal/monitoring/evidence");
                    if (response.statusCode() != 200) continue;
                    evidence = Arrays.asList(mapper.readValue(response.body(), MonitoringEvidenceController.Evidence[].class));
                }
                for (var e : evidence) {
                    String key = service.getKey() + ":" + e.key();
                    if (known.contains(key)) continue;
                    store.save(key, e.orderId(), e.type(), e.source(), e.destination(), e.stage(), e.status(),
                            Instant.parse(e.occurredAt()), e.eventId(), null, null, null);
                    known.add(key);
                }
                if (known.size() > 30000) known.clear();
            } catch (Exception failure) {
                LoggerFactory.getLogger(getClass()).debug("Monitoring evidence unavailable for {}", service.getKey(), failure);
            }
        }
    }
    @Scheduled(fixedDelay = 10000)
    public void checkHealth() {
        var result = new ArrayList<Health>();
        for (var service : urls.entrySet()) {
            String state; String detail;
            try {
                var response = get(service.getValue() + "/actuator/health");
                String status = mapper.readTree(response.body()).path("status").asText();
                state = response.statusCode() == 200 && status.equals("UP") ? "healthy" : "degraded";
                detail = "Actuator: " + (status.isBlank() ? "HTTP " + response.statusCode() : status);
            } catch (Exception failure) { state = "unavailable"; detail = "Health check could not connect within 2s"; }
            result.add(new Health(service.getKey(), state, detail, Instant.now()));
        }
        try (var admin = Admin.create(Map.of("bootstrap.servers", bootstrap, "request.timeout.ms", 2000, "default.api.timeout.ms", 2000))) {
            var nodes = admin.describeCluster().nodes().get(2, TimeUnit.SECONDS);
            result.add(new Health("kafka", nodes.isEmpty() ? "degraded" : "healthy", "Broker metadata check: " + nodes.size() + " broker(s)", Instant.now()));
        } catch (Exception failure) { result.add(new Health("kafka", "unavailable", "Broker metadata check failed within 2s", Instant.now())); }
        health = List.copyOf(result);
    }
    @Scheduled(fixedDelay = 60000)
    public void prune() { try { store.prune(); } catch (RuntimeException failure) { LoggerFactory.getLogger(getClass()).debug("Monitoring retention deferred", failure); } }
}
