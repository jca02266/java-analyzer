package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_SHIPPED   = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long      id;
    private Long      userId;
    private String    orderCode;
    private String    status;
    private BigDecimal totalAmount;
    private String    shippingAddress;
    private String    note;
    private LocalDateTime orderedAt;
    private LocalDateTime updatedAt;

    private List<OrderItem> items;
}
