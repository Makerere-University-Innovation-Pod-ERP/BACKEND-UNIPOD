package org.unipod.unipoderpbackend.repository.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.auth.ApiUser;

public interface ApiUserRepository extends JpaRepository<ApiUser, UUID> {
    Optional<ApiUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
