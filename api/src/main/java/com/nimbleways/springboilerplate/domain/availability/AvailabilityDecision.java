package com.nimbleways.springboilerplate.domain.availability;

public record AvailabilityDecision(Type type, boolean clearStock, int leadTime) {
    public enum Type {
        NONE,
        SELL,
        DELAY,
        OUT_OF_STOCK,
        EXPIRED
    }

    public static AvailabilityDecision none() {
        return new AvailabilityDecision(Type.NONE, false, 0);
    }

    public static AvailabilityDecision sell() {
        return new AvailabilityDecision(Type.SELL, false, 0);
    }

    public static AvailabilityDecision delay(int leadTime) {
        return new AvailabilityDecision(Type.DELAY, false, leadTime);
    }

    public static AvailabilityDecision outOfStock(boolean clearStock) {
        return new AvailabilityDecision(Type.OUT_OF_STOCK, clearStock, 0);
    }

    public static AvailabilityDecision expired() {
        return new AvailabilityDecision(Type.EXPIRED, true, 0);
    }
}
