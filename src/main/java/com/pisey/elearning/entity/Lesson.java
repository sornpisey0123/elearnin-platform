package com.pisey.elearning.entity;

import com.pisey.elearning.entity.enums.LessonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
        name = "lessons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lessons_section_id_sort_order",
                columnNames = {"section_id", "sort_order"}
        )
)
public class Lesson extends BaseEntity implements Ordered {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private LessonType type;

    /** Used when type is VIDEO. */
    @Size(max = 500)
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /** Used when type is VIDEO. */
    @Min(0)
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Used when type is TEXT. */
    @Column(name = "body", columnDefinition = "text")
    private String body;

    /** Used when type is FILE. */
    @Size(max = 500)
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @NotNull
    @Min(0)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder.Default
    @Column(name = "preview", nullable = false)
    private boolean preview = false;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LessonResource> resources = new ArrayList<>();

    public void addResource(LessonResource resource) {
        resources.add(resource);
        resource.setLesson(this);
    }

    public void removeResource(LessonResource resource) {
        resources.remove(resource);
        resource.setLesson(null);
    }
}
