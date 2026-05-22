package com.example.demo.controller;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> listByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.findByUser(userId));
    }

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        List<OrderItem> items = (List<OrderItem>) body.get("items");
        String address = (String) body.get("shippingAddress");
        Order order = orderService.createOrder(userId, items, address);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id) {
        orderService.confirmOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<Void> ship(@PathVariable Long id) {
        orderService.shipOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }
}
