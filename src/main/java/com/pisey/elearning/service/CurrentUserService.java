package com.pisey.elearning.service;

import com.pisey.elearning.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;

public interface CurrentUserService {

    /**
     * Returns the local user for the token's {@code sub}, creating it on first request and
     * syncing role, email and name from the token afterwards.
     */
    User getOrCreate(Jwt jwt);
}
