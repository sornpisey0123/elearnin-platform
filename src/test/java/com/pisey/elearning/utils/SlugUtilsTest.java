package com.pisey.elearning.utils;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void slugifyStripsAccentsAndPunctuation() {
        assertThat(SlugUtils.slugify("  Java & Spring Boot! Café ", 120)).isEqualTo("java-spring-boot-cafe");
    }

    @Test
    void slugifyTrimsToMaxLengthWithoutTrailingHyphen() {
        assertThat(SlugUtils.slugify("abc def", 4)).isEqualTo("abc");
    }

    @Test
    void uniqueSlugReturnsBaseWhenFree() {
        assertThat(SlugUtils.uniqueSlug("java", 220, Set.of()::contains)).isEqualTo("java");
    }

    @Test
    void uniqueSlugAppendsFirstFreeNumber() {
        Set<String> taken = Set.of("java", "java-2", "java-3");

        assertThat(SlugUtils.uniqueSlug("java", 220, taken::contains)).isEqualTo("java-4");
    }

    @Test
    void uniqueSlugKeepsWithinMaxLength() {
        Set<String> taken = Set.of("abcdefghij");

        String slug = SlugUtils.uniqueSlug("abcdefghij", 10, taken::contains);

        assertThat(slug).isEqualTo("abcdefgh-2").hasSizeLessThanOrEqualTo(10);
    }
}
