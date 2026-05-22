package com.example.demo.mapper;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {

    Optional<Order> findById(@Param("id") Long id);

    List<Order> findByUserId(@Param("userId") Long userId);

    List<Order> findByStatus(@Param("status") String status);

    List<Order> searchOrders(@Param("userId")  Long userId,
                             @Param("status")  String status,
                             @Param("keyword") String keyword);

    int insertOrder(Order order);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int insertOrderItem(OrderItem item);

    List<OrderItem> findItemsByOrderId(@Param("orderId") Long orderId);

    // XML 未対応メソッド（unmappedMethods 検出対象）
    int countPendingOrders(@Param("userId") Long userId);
}
