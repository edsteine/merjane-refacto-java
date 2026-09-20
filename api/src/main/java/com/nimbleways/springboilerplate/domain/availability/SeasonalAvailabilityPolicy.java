package com.nimbleways.springboilerplate.domain.availability;

import com.nimbleways.springboilerplate.entities.Product;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class SeasonalAvailabilityPolicy implements ProductAvailabilityPolicy {
    private static final String TYPE = "SEASONAL";

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public AvailabilityDecision decide(Product product, LocalDate today) {
        validate(product);
        if (today.isAfter(product.getSeasonStartDate())
                && today.isBefore(product.getSeasonEndDate())
                && product.getAvailable() > 0) {
            return AvailabilityDecision.sell();
        }
        return decideWhenUnavailable(product, today);
    }

    public AvailabilityDecision decideWhenUnavailable(Product product, LocalDate today) {
        validate(product);
        if (today.plusDays(product.getLeadTime()).isAfter(product.getSeasonEndDate())) {
            return AvailabilityDecision.outOfStock(true);
        }
        if (product.getSeasonStartDate().isAfter(today)) {
            return AvailabilityDecision.outOfStock(false);
        }
        return AvailabilityDecision.delay(product.getLeadTime());
    }

    private void validate(Product product) {
        ProductDataValidator.requireNonNegative(product.getAvailable(), "available");
        ProductDataValidator.requireNonNegative(product.getLeadTime(), "leadTime");
        ProductDataValidator.requirePresent(product.getSeasonStartDate(), "seasonStartDate");
        ProductDataValidator.requirePresent(product.getSeasonEndDate(), "seasonEndDate");
        if (product.getSeasonStartDate().isAfter(product.getSeasonEndDate())) {
            throw new InvalidProductException("seasonStartDate must not be after seasonEndDate");
        }
    }
}
