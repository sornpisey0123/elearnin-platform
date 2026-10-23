package com.pisey.elearning.repository.specification;

import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import static com.pisey.elearning.repository.specification.SpecificationUtils.ESCAPE;
import static com.pisey.elearning.repository.specification.SpecificationUtils.containsPattern;

/** Filters for the admin user list; a null/blank argument means "no filter". */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(Role role) {
        if (role == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** Case-insensitive "contains" on full name or email; % and _ in the keyword match literally. */
    public static Specification<User> keywordMatches(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Specification.unrestricted();
        }
        String pattern = containsPattern(keyword);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("email")), pattern, ESCAPE));
    }
}
