package com.pisey.elearning.repository.projection;

import com.pisey.elearning.entity.enums.Role;

/** Number of users with one role. */
public interface RoleCount {

    Role getRole();

    long getCount();
}
