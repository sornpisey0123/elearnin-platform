package com.pisey.elearning.repository.specification;

import java.util.Locale;

/** Helpers shared by the Specification classes. */
final class SpecificationUtils {

    /** Escape character passed to {@code cb.like(..., ESCAPE)}. */
    static final char ESCAPE = '\\';

    private SpecificationUtils() {
    }

    /** Lower-cased {@code %keyword%} pattern in which {@code %} and {@code _} match literally. */
    static String containsPattern(String keyword) {
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
