package com.zula.apihealth.service;

import java.io.InputStream;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to decide whether an HTTP status is considered "up".
 * Reads status ranges from classpath resource /status-ranges.txt (comma separated, supports ranges "200-399").
 * If the resource is missing or invalid, defaults to 200-399.
 */
public class StatusClassifier {
    private final List<IntRange> ranges = new ArrayList<>();

    public StatusClassifier() {
        this(loadSpecFromResource());
    }

    public StatusClassifier(String spec) {
        if (spec == null || spec.isBlank()) {
            spec = "200-399";
        }
        for (String part : spec.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.contains("-")) {
                String[] ab = p.split("-");
                try {
                    int a = Integer.parseInt(ab[0].trim());
                    int b = Integer.parseInt(ab[1].trim());
                    ranges.add(new IntRange(Math.min(a, b), Math.max(a, b)));
                } catch (NumberFormatException ignored) { }
            } else {
                try {
                    int v = Integer.parseInt(p);
                    ranges.add(new IntRange(v, v));
                } catch (NumberFormatException ignored) { }
            }
        }
        if (ranges.isEmpty()) {
            ranges.add(new IntRange(200, 399));
        }
    }

    /** Returns true if status falls in any configured range. Null -> false. */
    public boolean isUp(Integer status) {
        if (status == null) return false;
        int s = status;
        for (IntRange r : ranges) {
            if (s >= r.start && s <= r.end) return true;
        }
        return false;
    }

    private static class IntRange {
        final int start, end;
        IntRange(int start, int end) { this.start = start; this.end = end; }
    }

    private static String loadSpecFromResource() {
        try (InputStream is = StatusClassifier.class.getResourceAsStream("/status-ranges.properties")) {
            Properties props = new Properties();
            if (is != null) {
                props.load(is);
            }
            String spec = props.getProperty("up.status.ranges", "200-399");
            return spec == null || spec.isBlank() ? "200-399" : spec.trim();
        } catch (Exception e) {
            return "200-399";
        }
    }
}
