package com.avery.atrain.model;

/** 沿路線站點順序的行駛方向 */
public enum TravelDirection {
    FORWARD,
    REVERSE;

    public TravelDirection opposite() {
        return this == FORWARD ? REVERSE : FORWARD;
    }

    public static TravelDirection fromString(String raw) {
        if (raw == null) return FORWARD;
        return "reverse".equalsIgnoreCase(raw) ? REVERSE : FORWARD;
    }

    public String toDataString() {
        return name().toLowerCase();
    }
}
