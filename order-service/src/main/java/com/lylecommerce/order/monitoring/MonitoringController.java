package com.lylecommerce.order.monitoring;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/monitoring")
public class MonitoringController {
    private final MonitoringStore store;
    private final MonitoringCollector collector;
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private static class Client {
        final SseEmitter emitter = new SseEmitter(0L);
        long cursor;
        Client(long cursor) { this.cursor = cursor; }
    }
    public MonitoringController(MonitoringStore store, MonitoringCollector collector) { this.store = store; this.collector = collector; }
    @GetMapping("/events")
    public List<MonitorEvent> history(@RequestParam(name = "orderId", defaultValue = "") String orderId,
            @RequestParam(name = "type", defaultValue = "") String type, @RequestParam(name = "service", defaultValue = "") String service,
            @RequestParam(name = "limit", defaultValue = "500") int limit) {
        return store.read(0, orderId, type, service, limit, true);
    }
    @GetMapping("/health")
    public List<MonitoringCollector.Health> health() { return collector.health(); }
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public synchronized SseEmitter stream(@RequestHeader(value = "Last-Event-ID", defaultValue = "0") String lastId,
            @RequestParam(name = "since", defaultValue = "0") String since) {
        if (clients.size() >= 100) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Monitor connection limit reached");
        long cursor;
        try { cursor = Math.max(0, Long.parseLong(lastId)); if (cursor == 0) cursor = Math.max(0, Long.parseLong(since)); } catch (NumberFormatException invalid) { cursor = 0; }
        var client = new Client(cursor);
        client.emitter.onCompletion(() -> clients.remove(client));
        client.emitter.onTimeout(() -> clients.remove(client));
        client.emitter.onError(error -> clients.remove(client));
        synchronized (client) {
            try {
                if (cursor == 0 || cursor < store.min() || cursor > store.max()) {
                    var snapshot = store.read(0, "", "", "", 500, true);
                    sendEvents(client, "snapshot", snapshot);
                }
                client.emitter.send(SseEmitter.event().name("health").data(collector.health()).reconnectTime(2000));
                clients.add(client);
            } catch (Exception failure) { client.emitter.completeWithError(failure); }
        }
        return client.emitter;
    }
    private void sendEvents(Client client, String name, List<MonitorEvent> events) throws Exception {
        long cursor = events.isEmpty() ? client.cursor : events.getLast().sequence();
        client.emitter.send(SseEmitter.event().name(name).id(Long.toString(cursor)).data(events).reconnectTime(2000));
        client.cursor = cursor;
    }
    @Scheduled(fixedDelay = 1000)
    public void broadcast() {
        for (var client : clients) synchronized (client) {
            try {
                if (client.cursor > 0 && client.cursor < store.min()) sendEvents(client, "snapshot", store.read(0, "", "", "", 500, true));
                var events = store.read(client.cursor, "", "", "", 200, false);
                if (!events.isEmpty()) sendEvents(client, "events", events);
                client.emitter.send(SseEmitter.event().name("health").data(collector.health()));
                client.emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception failure) { clients.remove(client); client.emitter.completeWithError(failure); }
        }
    }
}
