package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper  userMapper;

    public OrderService(OrderMapper orderMapper, UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.userMapper  = userMapper;
    }

    public Optional<Order> findById(Long id) {
        return orderMapper.findById(id);
    }

    public List<Order> findByUser(Long userId) {
        return orderMapper.findByUserId(userId);
    }

    /**
     * 注文作成。ユーザー存在確認・金額集計・ステータス設定を含む複雑なメソッド。
     * ネスト深度・外部呼び出し・連続代入ブロック検出対象。
     */
    @Transactional
    public Order createOrder(Long userId, List<OrderItem> items, String shippingAddress) {
        // ユーザー確認
        userMapper.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // アイテムバリデーション
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }

        // 金額集計
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Invalid quantity: " + item.getQuantity());
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Invalid unit price for product: " + item.getProductId());
            }
            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setSubtotal(subtotal);
            total = total.add(subtotal);
        }

        // 上限チェック（マジックナンバー）
        if (total.compareTo(new BigDecimal(1000000)) > 0) {
            throw new IllegalArgumentException("Order total exceeds limit: " + total);
        }

        // 連続代入ブロック
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderCode(generateOrderCode());
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(total);
        order.setShippingAddress(shippingAddress);
        order.setNote("");
        order.setOrderedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderMapper.insertOrder(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderMapper.insertOrderItem(item);
        }

        return order;
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        changeStatus(orderId, Order.STATUS_PENDING, Order.STATUS_CONFIRMED);
    }

    @Transactional
    public void shipOrder(Long orderId) {
        changeStatus(orderId, Order.STATUS_CONFIRMED, Order.STATUS_SHIPPED);
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderMapper.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (Order.STATUS_SHIPPED.equals(order.getStatus()) ||
            Order.STATUS_DELIVERED.equals(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }

        orderMapper.updateStatus(orderId, Order.STATUS_CANCELLED);
    }

    public List<String> getPendingOrderCodes(Long userId) {
        return orderMapper.findByUserId(userId).stream()
                .filter(o -> Order.STATUS_PENDING.equals(o.getStatus()))
                .map(Order::getOrderCode)
                .collect(Collectors.toList());
    }

    private void changeStatus(Long orderId, String expectedCurrent, String next) {
        Order order = orderMapper.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (!expectedCurrent.equals(order.getStatus())) {
            throw new IllegalStateException(
                "Expected status " + expectedCurrent + " but was: " + order.getStatus());
        }
        orderMapper.updateStatus(orderId, next);
    }

    private String generateOrderCode() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // デッドコード候補
    private boolean isHighValueOrder(Order order) {
        return order.getTotalAmount().compareTo(new BigDecimal(100000)) >= 0;
    }
}
