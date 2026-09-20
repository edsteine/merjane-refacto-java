package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {
    private final OrderRepository orderRepository;
    private final ProductService productService;

    public OrderProcessingService(OrderRepository orderRepository, ProductService productService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
    }

    public Long processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).get();
        order.getItems().forEach(productService::process);
        return order.getId();
    }
}
