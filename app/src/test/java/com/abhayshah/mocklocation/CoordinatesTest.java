package com.abhayshah.mocklocation;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoordinatesTest {
    @Test public void acceptsGeographicBoundaries() {
        assertTrue(Coordinates.isValid(-90, -180));
        assertTrue(Coordinates.isValid(90, 180));
        assertTrue(Coordinates.isValid(43.6532, -79.3832));
    }

    @Test public void rejectsOutOfRangeAndNonFiniteValues() {
        assertFalse(Coordinates.isValid(-90.0001, 0));
        assertFalse(Coordinates.isValid(0, 180.0001));
        assertFalse(Coordinates.isValid(Double.NaN, 0));
        assertFalse(Coordinates.isValid(0, Double.POSITIVE_INFINITY));
    }
}
