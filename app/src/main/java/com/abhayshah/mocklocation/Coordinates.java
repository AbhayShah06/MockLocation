package com.abhayshah.mocklocation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validation shared by the screen and location service. */
public final class Coordinates {
    private Coordinates() { }

    public static boolean isValid(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    public static double[] parse(String input) {
        if (input == null) return null; String text;
        try { text = URLDecoder.decode(input.trim(), StandardCharsets.UTF_8.name()); } catch (Exception ignored) { text = input.trim(); }
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("@(-?\\d{1,2}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?)"),
                Pattern.compile("(?:[?&](?:q|query|ll)=|geo:)(-?\\d{1,2}(?:\\.\\d+)?)[, ]+(-?\\d{1,3}(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:^|\\s)(-?\\d{1,2}(?:\\.\\d+)?)[, ]+(-?\\d{1,3}(?:\\.\\d+)?)(?:$|\\s)")};
        for (Pattern pattern : patterns) { Matcher match = pattern.matcher(text); if (!match.find()) continue; try { double lat = Double.parseDouble(match.group(1)), lon = Double.parseDouble(match.group(2)); if (isValid(lat, lon)) return new double[]{lat, lon}; } catch (RuntimeException ignored) { } }
        return null;
    }
}
