package com.nimbleways.springboilerplate.domain.availability;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductAvailabilityPolicies {
    private final List<ProductAvailabilityPolicy> policies;

    public ProductAvailabilityPolicies(List<ProductAvailabilityPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

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
