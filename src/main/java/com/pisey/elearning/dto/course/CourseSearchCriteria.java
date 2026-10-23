package com.pisey.elearning.dto.course;

import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;

/** Catalogue filters; every field is optional. */
public record CourseSearchCriteria(
        String keyword,
        Long categoryId,
        CourseLevel level,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {

    /** Catalogue sort options ({@code ?sort=newest|rating|popular}); id breaks ties so paging is stable. */
    public enum CourseSort {
        NEWEST(Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))),
        RATING(Sort.by(Sort.Order.desc("avgRating"), Sort.Order.desc("reviewCount"), Sort.Order.desc("id"))),
        POPULAR(Sort.by(Sort.Order.desc("enrollmentCount"), Sort.Order.desc("id")));

        private final Sort sort;

        CourseSort(Sort sort) {
            this.sort = sort;
        }

        public Sort sort() {
            return sort;
        }

        /** Case-insensitive; null/blank means NEWEST. */
        public static CourseSort from(String value) {
            if (value == null || value.isBlank()) {
                return NEWEST;
            }
            return Arrays.stream(values())
                    .filter(option -> option.name().equals(value.trim().toUpperCase(Locale.ROOT)))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Unknown sort: " + value + "; use newest, rating or popular"));
        }
    }
}
