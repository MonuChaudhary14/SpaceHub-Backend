package org.spacehub.repository;

import org.spacehub.entities.User.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<User> findByUsernameIgnoreCase(String username);

  Page<User> findByUsernameContainingIgnoreCaseAndEmailNot(String username, String email, Pageable pageable);

}
