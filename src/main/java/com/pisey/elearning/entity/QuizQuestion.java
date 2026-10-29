package com.pisey.elearning.entity;

import com.pisey.elearning.entity.enums.QuestionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "quiz_questions")
public class QuizQuestion extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @NotBlank
    @Column(name = "question_text", columnDefinition = "text", nullable = false)
    private String questionText;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private QuestionType type;

    @NotNull
    @Min(0)
    @Builder.Default
    @Column(name = "points", nullable = false)
    private Integer points = 1;

    @NotNull
    @Min(0)
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuizOption> options = new ArrayList<>();

    public void addOption(QuizOption option) {
        options.add(option);
        option.setQuestion(this);
    }

    public void removeOption(QuizOption option) {
        options.remove(option);
        option.setQuestion(null);
    }
}
