package org.unipod.unipoderpbackend.repository.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public interface ApiUserRepository extends JpaRepository<ApiUser, UUID> {
    Optional<ApiUser> findByEmailIgnoreCase(String email);

    List<ApiUser> findAllByRoleInAndEnabledTrue(Collection<ApiUserRole> roles);

    Optional<ApiUser> findByPasswordSetupTokenHash(String passwordSetupTokenHash);

    boolean existsByEmailIgnoreCase(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ApiUser user
            set user.passwordHash = :passwordHash,
                user.mustChangePassword = false,
                user.passwordSetupTokenHash = null,
                user.passwordSetupExpiresAt = null,
                user.updatedAt = :now
            where user.passwordSetupTokenHash = :tokenHash
              and user.passwordSetupExpiresAt > :now
              and user.enabled = true
            """)
    int consumePasswordSetupToken(
            @Param("tokenHash") String tokenHash,
            @Param("passwordHash") String passwordHash,
            @Param("now") Instant now
    );
}
