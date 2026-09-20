package com.nimbleways.springboilerplate.services.implementations;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository pr;
    private final NotificationService ns;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, NotificationService notificationService, Clock clock) {
        this.pr = productRepository;
        this.ns = notificationService;
        this.clock = clock;
    }

    public void notifyDelay(int leadTime, Product p) {
        p.setLeadTime(leadTime);
        pr.save(p);
        ns.sendDelayNotification(leadTime, p.getName());
    }

    public void handleSeasonalProduct(Product p) {
        if (LocalDate.now(clock).plusDays(p.getLeadTime()).isAfter(p.getSeasonEndDate())) {
            ns.sendOutOfStockNotification(p.getName());
            p.setAvailable(0);
            pr.save(p);
        } else if (p.getSeasonStartDate().isAfter(LocalDate.now(clock))) {
            ns.sendOutOfStockNotification(p.getName());
            pr.save(p);
        } else {
            notifyDelay(p.getLeadTime(), p);
        }
    }

    public void handleExpiredProduct(Product p) {
        if (p.getAvailable() > 0 && p.getExpiryDate().isAfter(LocalDate.now(clock))) {
            p.setAvailable(p.getAvailable() - 1);
            pr.save(p);
        } else {
            ns.sendExpirationNotification(p.getName(), p.getExpiryDate());
            p.setAvailable(0);
            pr.save(p);
        }
    }
}
