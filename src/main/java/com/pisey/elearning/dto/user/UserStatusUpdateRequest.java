package com.pisey.elearning.dto.user;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(

        @NotNull
        Boolean active
) {
}
