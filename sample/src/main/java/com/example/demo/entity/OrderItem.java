package com.example.demo.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItem {
    private Long       id;
    private Long       orderId;
    private Long       productId;
    private String     productName;
    private int        quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
