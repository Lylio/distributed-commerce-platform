package com.lylecommerce.order.api;
import com.lylecommerce.order.application.OrderDashboardService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/orders")
public class OrderDashboardController {
    private final OrderDashboardService dashboard;
    public OrderDashboardController(OrderDashboardService dashboard) { this.dashboard = dashboard; }
    @GetMapping public List<OrderDashboardService.Detail> list(
            @RequestParam(name = "customerId", required = false) UUID customerId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) { return dashboard.list(customerId, limit); }
    @GetMapping("/{id}/details") public OrderDashboardService.Detail details(@PathVariable("id") UUID id) { return dashboard.detail(id); }
}
