package com.sgmart.order;

public enum ActorType {
    CUSTOMER("Customer"),
    ADMIN("Store"),
    SYSTEM("System");

    private final String label;

    ActorType(String label) {
        this.label = label;
    }

    /** How the "Order updates" list names who did it. */
    public String label() {
        return label;
    }
}
