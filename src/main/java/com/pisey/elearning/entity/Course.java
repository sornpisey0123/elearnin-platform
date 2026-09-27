package com.pisey.elearning.entity;

import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_courses_slug", columnNames = "slug"),
        indexes = {
                @Index(name = "idx_courses_category_id", columnList = "category_id"),
                @Index(name = "idx_courses_instructor_id", columnList = "instructor_id"),
                @Index(name = "idx_courses_level", columnList = "level"),
                @Index(name = "idx_courses_price", columnList = "price"),
                @Index(name = "idx_courses_status", columnList = "status"),
                @Index(name = "idx_courses_published_at", columnList = "published_at")
        },
        check = @CheckConstraint(name = "ck_courses_price", constraint = "price >= 0")
)
public class Course extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @NotBlank
    @Size(max = 220)
    @Column(name = "slug", length = 220, nullable = false)
    private String slug;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 30, nullable = false)
    private CourseLevel level;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Size(max = 500)
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private CourseStatus status = CourseStatus.DRAFT;

    /** Cached aggregate of review ratings. */
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("5.00")
    @Builder.Default
    @Column(name = "avg_rating", precision = 3, scale = 2, nullable = false)
    private BigDecimal avgRating = BigDecimal.ZERO;

    /** Cached count of reviews. */
    @NotNull
    @Min(0)
    @Builder.Default
    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    /** Cached count of enrollments. */
    @NotNull
    @Min(0)
    @Builder.Default
    @Column(name = "enrollment_count", nullable = false)
    private Integer enrollmentCount = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Section> sections = new ArrayList<>();

    public void addSection(Section section) {
        sections.add(section);
        section.setCourse(this);
    }

    public void removeSection(Section section) {
        sections.remove(section);
        section.setCourse(null);
    }

    public void publish() {
        this.status = CourseStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void archive() {
        this.status = CourseStatus.ARCHIVED;
        this.archivedAt = Instant.now();
    }
}
