package org.spacehub.specifications;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CommunitySpecification {

    public static Specification<Community> filter(String name, String creatorName) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }

            if (creatorName != null && !creatorName.isBlank()) {
                Join<Community, User> creatorJoin = root.join("createdBy");
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(creatorJoin.get("name")), "%" + creatorName.toLowerCase() + "%"));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

}
