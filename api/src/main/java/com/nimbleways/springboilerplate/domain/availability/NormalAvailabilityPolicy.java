package com.nimbleways.springboilerplate.domain.availability;

import com.nimbleways.springboilerplate.entities.Product;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class NormalAvailabilityPolicy implements ProductAvailabilityPolicy {
    private static final String TYPE = "NORMAL";

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public AvailabilityDecision decide(Product product, LocalDate today) {
        ProductDataValidator.requireNonNegative(product.getAvailable(), "available");
        ProductDataValidator.requireNonNegative(product.getLeadTime(), "leadTime");
        if (product.getAvailable() > 0) {
            return AvailabilityDecision.sell();
        }
        return product.getLeadTime() > 0
                ? AvailabilityDecision.delay(product.getLeadTime())
                : AvailabilityDecision.none();
    }
}
