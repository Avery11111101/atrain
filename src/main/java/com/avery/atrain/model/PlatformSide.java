package com.avery.atrain.model;

/** 站點內的去程／回程乘車月台 */
public enum PlatformSide {
    FORWARD,
    RETURN;

    public TravelDirection toTravelDirection() {
        return this == FORWARD ? TravelDirection.FORWARD : TravelDirection.REVERSE;
    }

    public static PlatformSide fromString(String raw) {
        if (raw == null) return FORWARD;
        return "return".equalsIgnoreCase(raw) ? RETURN : FORWARD;
    }

    public String toDataString() {
        return name().toLowerCase();
    }
}
