package com.nimbleways.springboilerplate.services.implementations;

import java.time.Clock;
import java.time.LocalDate;

import com.nimbleways.springboilerplate.domain.availability.AvailabilityDecision;
import com.nimbleways.springboilerplate.domain.availability.ProductAvailabilityPolicies;
import com.nimbleways.springboilerplate.domain.availability.ProductAvailabilityPolicy;
import com.nimbleways.springboilerplate.domain.availability.ExpirableAvailabilityPolicy;
import com.nimbleways.springboilerplate.domain.availability.SeasonalAvailabilityPolicy;
import org.springframework.stereotype.Service;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository pr;
    private final NotificationService ns;
    private final Clock clock;
    private final ProductAvailabilityPolicies policies;

    public ProductService(ProductRepository productRepository, NotificationService notificationService, Clock clock) {
        this.pr = productRepository;
        this.ns = notificationService;
        this.clock = clock;
        this.policies = new ProductAvailabilityPolicies();
    }

    public void notifyDelay(int leadTime, Product p) {
        p.setLeadTime(leadTime);
        pr.save(p);
        ns.sendDelayNotification(leadTime, p.getName());
    }

    public void process(Product product) {
        ProductAvailabilityPolicy policy = policies.forType(product.getType());
        if (policy != null) {
            apply(product, policy.decide(product, LocalDate.now(clock)));
        }
    }

    public void handleSeasonalProduct(Product p) {
        LocalDate today = LocalDate.now(clock);
        AvailabilityDecision decision = new SeasonalAvailabilityPolicy().decideWhenUnavailable(p, today);
        apply(p, decision);
    }

    public void handleExpiredProduct(Product p) {
        apply(p, new ExpirableAvailabilityPolicy().decide(p, LocalDate.now(clock)));
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
