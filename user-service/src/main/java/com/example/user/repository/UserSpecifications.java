package com.example.user.repository;

import com.example.user.model.User;
import com.example.user.model.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the WHERE clause for user searches from optional filters.
 * Adding a new filter later = one more "if" block here, nothing else.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> matching(String text, String email, UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // No status filter -> hide deleted users; an explicit status shows exactly that status.
            if (status == null) {
                predicates.add(cb.notEqual(root.get("status"), UserStatus.DELETED));
            } else {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(email)) {
                predicates.add(cb.equal(root.get("email"), email.trim().toLowerCase(Locale.ROOT)));
            }

            if (StringUtils.hasText(text)) {
                String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\')));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** '%' and '_' typed by a user are searched literally, not used as SQL wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
