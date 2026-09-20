package com.nimbleways.springboilerplate.domain.availability;

import com.nimbleways.springboilerplate.entities.Product;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ExpirableAvailabilityPolicy implements ProductAvailabilityPolicy {
    private static final String TYPE = "EXPIRABLE";

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public AvailabilityDecision decide(Product product, LocalDate today) {
        ProductDataValidator.requireNonNegative(product.getAvailable(), "available");
        ProductDataValidator.requirePresent(product.getExpiryDate(), "expiryDate");
        return product.getAvailable() > 0 && product.getExpiryDate().isAfter(today)
                ? AvailabilityDecision.sell()
                : AvailabilityDecision.expired();
    }
}
