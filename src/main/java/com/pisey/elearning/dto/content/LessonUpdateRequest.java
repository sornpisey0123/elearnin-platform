package com.pisey.elearning.dto.content;

import com.pisey.elearning.entity.enums.LessonType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Same per-type rules as {@link LessonCreateRequest}. Changing the type away from FILE removes the uploaded file.
 */
public record LessonUpdateRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotNull
        LessonType type,

        @Size(max = 500)
        @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL")
        String videoUrl,

        @Min(1)
        Integer durationSeconds,

        @Size(max = 200_000)
        String body,

        /** Defaults to false. */
        Boolean preview
) {
}
