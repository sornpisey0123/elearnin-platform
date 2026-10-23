package com.pisey.elearning.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

    private SlugUtils() {
    }

    /** "Java & Spring Boot!" -> "java-spring-boot" */
    public static String slugify(String input, int maxLength) {
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD);
        String slug = DIACRITICS.matcher(normalized).replaceAll("").toLowerCase(Locale.ROOT);
        slug = NON_ALPHANUMERIC.matcher(slug).replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
        if (slug.length() > maxLength) {
            slug = EDGE_HYPHENS.matcher(slug.substring(0, maxLength)).replaceAll("");
        }
        return slug;
    }

    /**
     * Returns {@code base}, or {@code base-2}, {@code base-3}… for the first value {@code exists} rejects,
     * trimming {@code base} so the result never exceeds {@code maxLength}.
     */
    public static String uniqueSlug(String base, int maxLength, Predicate<String> exists) {
        if (!exists.test(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String suffix = "-" + n;
            String head = base.length() + suffix.length() > maxLength
                    ? EDGE_HYPHENS.matcher(base.substring(0, maxLength - suffix.length())).replaceAll("")
                    : base;
            String candidate = head + suffix;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
    }
}
