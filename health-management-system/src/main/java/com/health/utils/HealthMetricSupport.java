package com.health.utils;

import java.util.Map;
import java.util.Set;

public final class HealthMetricSupport {

    private static final Set<String> CUMULATIVE_TYPES = Set.of("steps", "sleep", "exercise", "water");
    private static final Map<String, double[]> NORMAL_RANGES = Map.of(
            "steps", new double[]{5000, 12000},
            "heart_rate", new double[]{60, 100},
            "sleep", new double[]{6, 9},
            "weight", new double[]{50, 90},
            "blood_pressure", new double[]{90, 140},
            "blood_sugar", new double[]{3.9, 7.0},
            "diet", new double[]{0, 2500},
            "exercise", new double[]{0, 90},
            "mood", new double[]{1, 5}
    );

    private HealthMetricSupport() {
    }

    public static boolean isCumulative(String type) {
        return type != null && CUMULATIVE_TYPES.contains(type);
    }

    public static double[] getNormalRange(String type) {
        return type == null ? null : NORMAL_RANGES.get(type);
    }
}
