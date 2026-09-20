package com.nimbleways.springboilerplate.domain.availability;

import com.nimbleways.springboilerplate.entities.Product;
import java.time.LocalDate;

public interface ProductAvailabilityPolicy {
    boolean supports(String type);

    AvailabilityDecision decide(Product product, LocalDate today);
}
