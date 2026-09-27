package com.pisey.elearning.entity;

import com.pisey.elearning.entity.enums.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollments_student_id_course_id",
                columnNames = {"student_id", "course_id"}
        ),
        indexes = @Index(name = "idx_enrollments_course_id_enrolled_at", columnList = "course_id, enrolled_at")
)
public class Enrollment extends BaseEntity {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Snapshot of the course price at enrollment time; used for revenue reporting. */
    @NotNull
    @DecimalMin("0.00")
    @Column(name = "price_paid", precision = 10, scale = 2, nullable = false)
    private BigDecimal pricePaid;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Builder.Default
    @Column(name = "progress_pct", precision = 5, scale = 2, nullable = false)
    private BigDecimal progressPct = BigDecimal.ZERO;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (enrolledAt == null) {
            enrolledAt = Instant.now();
        }
    }

    public void markCompleted() {
        this.status = EnrollmentStatus.COMPLETED;
        this.progressPct = ONE_HUNDRED;
        this.completedAt = Instant.now();
    }
}
