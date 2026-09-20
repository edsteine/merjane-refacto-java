package com.nimbleways.springboilerplate.domain.availability;

import java.util.Objects;

final class ProductDataValidator {
    private ProductDataValidator() {
    }

    static void requireNonNegative(Integer value, String field) {
        requirePresent(value, field);
        if (value < 0) {
            throw new InvalidProductException(field + " must not be negative");
        }
    }

    static void requirePresent(Object value, String field) {
        if (Objects.isNull(value)) {
            throw new InvalidProductException(field + " is required");
        }
    }
}
