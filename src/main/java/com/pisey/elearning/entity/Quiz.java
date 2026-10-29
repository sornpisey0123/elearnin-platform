package com.pisey.elearning.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
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
        name = "quizzes",
        check = {
                @CheckConstraint(name = "ck_quizzes_pass_mark", constraint = "pass_mark BETWEEN 0 AND 100"),
                @CheckConstraint(name = "ck_quizzes_single_parent", constraint = "(lesson_id IS NULL) <> (section_id IS NULL)")
        }
)
public class Quiz extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "pass_mark", nullable = false)
    private Integer passMark;

    @Min(1)
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        question.setQuiz(this);
    }

    public void removeQuestion(QuizQuestion question) {
        questions.remove(question);
        question.setQuiz(null);
    }

    /** The course the quiz belongs to, through its lesson or its section (derived; not a column). */
    public Course getCourse() {
        return lesson != null ? lesson.getSection().getCourse() : section.getCourse();
    }

    @PrePersist
    @PreUpdate
    void validateParent() {
        if ((lesson == null) == (section == null)) {
            throw new IllegalStateException("Quiz must belong to exactly one of lesson or section");
        }
    }
}
