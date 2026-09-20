package com.nimbleways.springboilerplate.domain.availability;

import java.util.List;

public class ProductAvailabilityPolicies {
    private final List<ProductAvailabilityPolicy> policies = List.of(
            new NormalAvailabilityPolicy(),
            new SeasonalAvailabilityPolicy(),
            new ExpirableAvailabilityPolicy());

    public ProductAvailabilityPolicy forType(String type) {
        if (type == null) {
            throw new NullPointerException("Product type must not be null");
        }
        return policies.stream()
                .filter(policy -> policy.supports(type))
                .findFirst()
                .orElse(null);
    }
}
