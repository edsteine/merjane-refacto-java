package com.nimbleways.springboilerplate.services.implementations;

import java.time.Clock;
import java.time.LocalDate;

import com.nimbleways.springboilerplate.domain.availability.AvailabilityDecision;
import com.nimbleways.springboilerplate.domain.availability.InvalidProductException;
import com.nimbleways.springboilerplate.domain.availability.ProductAvailabilityPolicies;
import com.nimbleways.springboilerplate.domain.availability.ProductAvailabilityPolicy;
import org.springframework.stereotype.Service;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository pr;
    private final NotificationService ns;
    private final Clock clock;
    private final ProductAvailabilityPolicies policies;

    public ProductService(ProductRepository productRepository, NotificationService notificationService, Clock clock,
            ProductAvailabilityPolicies policies) {
        this.pr = productRepository;
        this.ns = notificationService;
        this.clock = clock;
        this.policies = policies;
    }

    public void notifyDelay(int leadTime, Product p) {
        p.setLeadTime(leadTime);
        pr.save(p);
        ns.sendDelayNotification(leadTime, p.getName());
    }

    public void process(Product product) {
        if (product == null || product.getType() == null) {
            throw new InvalidProductException("type is required");
        }
        ProductAvailabilityPolicy policy = policies.forType(product.getType());
        if (policy != null) {
            apply(product, policy.decide(product, LocalDate.now(clock)));
        }
    }

    private void apply(Product product, AvailabilityDecision decision) {
        switch (decision.type()) {
            case SELL -> saveStock(product, product.getAvailable() - 1);
            case DELAY -> notifyDelay(decision.leadTime(), product);
            case OUT_OF_STOCK -> {
                ns.sendOutOfStockNotification(product.getName());
                if (decision.clearStock()) {
                    product.setAvailable(0);
                }
                pr.save(product);
            }
            case EXPIRED -> {
                ns.sendExpirationNotification(product.getName(), product.getExpiryDate());
                saveStock(product, 0);
            }
            case NONE -> { }
        }
    }

    private void saveStock(Product product, int available) {
        product.setAvailable(available);
        pr.save(product);
    }
}
