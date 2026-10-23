package com.pisey.elearning.repository.specification;

import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static com.pisey.elearning.repository.specification.SpecificationUtils.ESCAPE;
import static com.pisey.elearning.repository.specification.SpecificationUtils.containsPattern;

/** Catalogue and instructor-list filters; a null/blank argument means "no filter". */
public final class CourseSpecifications {

    private CourseSpecifications() {
    }

    public static Specification<Course> hasStatus(CourseStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Case-insensitive "contains" on title or description. */
    public static Specification<Course> keywordMatches(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Specification.unrestricted();
        }
        String pattern = containsPattern(keyword);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
    }

    public static Specification<Course> inCategory(Long categoryId) {
        if (categoryId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Course> hasLevel(CourseLevel level) {
        if (level == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    public static Specification<Course> priceAtLeast(BigDecimal minPrice) {
        if (minPrice == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Course> priceAtMost(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Course> ownedBy(UUID instructorKeycloakId) {
        return (root, query, cb) -> cb.equal(root.get("instructor").get("keycloakId"), instructorKeycloakId);
    }
}
