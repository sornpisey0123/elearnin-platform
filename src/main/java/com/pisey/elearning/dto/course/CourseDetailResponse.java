package com.pisey.elearning.dto.course;

import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Course page: the course, its instructor and category, and the size of its outline. */
public record CourseDetailResponse(
        Long id,
        String title,
        String slug,
        String description,
        CourseLevel level,
        BigDecimal price,
        String thumbnailUrl,
        CourseStatus status,
        BigDecimal avgRating,
        int reviewCount,
        int enrollmentCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        InstructorInfo instructor,
        CategoryInfo category,
        long sectionCount,
        long lessonCount,
        long totalDurationSeconds
) {

    public record InstructorInfo(Long id, String fullName, String avatarUrl, String bio) {
    }

    public record CategoryInfo(Long id, String name, String slug) {
    }
}
