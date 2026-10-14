package com.abhayshah.mocklocation;

/** Validation shared by the screen and location service. */
public final class Coordinates {
    private Coordinates() { }

    public static boolean isValid(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }
}
