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

    @Test public void parsesCoordinatesAndCommonMapLinks() {
        assertArrayEquals(new double[]{43.6532, -79.3832}, Coordinates.parse("43.6532, -79.3832"), 0.000001);
        assertArrayEquals(new double[]{43.6532, -79.3832}, Coordinates.parse("https://maps.google.com/place/x/@43.6532,-79.3832,15z"), 0.000001);
        assertArrayEquals(new double[]{43.6532, -79.3832}, Coordinates.parse("geo:43.6532,-79.3832"), 0.000001);
        assertNull(Coordinates.parse("not a place"));
        assertNull(Coordinates.parse("91, 200"));
    }
}
