package com.chenx.learning.serivce;


import com.chenx.learning.dao.OrderItemMapper;
import com.chenx.learning.dao.OrderMapper;
import com.chenx.learning.pojo.Order;
import com.chenx.learning.pojo.OrderItem;
import com.chenx.learning.util.DaoUtils;
import com.google.common.base.Preconditions;

import java.math.BigDecimal;
import java.util.List;

public class OrderService {
    /**
     * 创建订单，并分别将其条目入库
     * @param order order对象，内部包括item
     * @return 订单id
     */
    public long createOrder(Order order) {
        Preconditions.checkArgument(order != null, "order is null");
        Preconditions.checkArgument(
                order.getOrderItems() != null && order.getOrderItems().size() > 0,
                "orderItems is empty");
        return DaoUtils.execute(sqlSession -> {
            OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
            OrderItemMapper orderItemMapper = sqlSession.getMapper(OrderItemMapper.class);
            long affected = orderMapper.save(order);
            if (affected < 0) {
                throw new RuntimeException("Save order failed...");
            }
            long orderId = order.getId();
            for (OrderItem orderItem : order.getOrderItems()) {
                orderItemMapper.save(orderItem, orderId);
            }
            return orderId;
        });
    }

    public Order find(long id) {
        Preconditions.checkArgument(id > 0, "order id is empty");
        return DaoUtils.execute(sqlSession -> {
            OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
            OrderItemMapper orderItemMapper = sqlSession.getMapper(OrderItemMapper.class);
            Order order = orderMapper.find(id);
            if (order == null) return null;
            List<OrderItem> orderItems = orderItemMapper.findByOrderId(id);
            order.setOrderItems(orderItems);
            order.setTotalPrice(calcTotalPrice(order));
            return order;
        });
    }
    private BigDecimal calcTotalPrice(Order order) {
        List<OrderItem> orderItems = order.getOrderItems();
        BigDecimal totalPrice = new BigDecimal(0);
        for (OrderItem item : orderItems) {
            BigDecimal itemPrice = item.getProduct().getPrice().multiply(new BigDecimal(item.getAmount()));
            item.setPrice(itemPrice);
            totalPrice.add(itemPrice);
        }
        return totalPrice;
    }
}
