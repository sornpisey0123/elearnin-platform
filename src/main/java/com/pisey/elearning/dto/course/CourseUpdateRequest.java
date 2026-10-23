package com.pisey.elearning.dto.course;

import com.pisey.elearning.entity.enums.CourseLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Full replacement of the editable fields. The slug follows the title only until the course is first published. */
public record CourseUpdateRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 20000)
        String description,

        @NotNull
        Long categoryId,

        @NotNull
        CourseLevel level,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price
) {
}
