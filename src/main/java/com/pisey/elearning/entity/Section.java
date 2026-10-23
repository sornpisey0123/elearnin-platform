package com.pisey.elearning.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "sections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sections_course_id_sort_order",
                columnNames = {"course_id", "sort_order"}
        )
)
public class Section extends BaseEntity implements Ordered {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @NotNull
    @Min(0)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Lesson> lessons = new ArrayList<>();

    public void addLesson(Lesson lesson) {
        lessons.add(lesson);
        lesson.setSection(this);
    }

    public void removeLesson(Lesson lesson) {
        lessons.remove(lesson);
        lesson.setSection(null);
    }
}
