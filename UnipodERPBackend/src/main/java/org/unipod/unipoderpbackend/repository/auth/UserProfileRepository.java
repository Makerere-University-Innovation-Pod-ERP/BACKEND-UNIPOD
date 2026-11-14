package org.unipod.unipoderpbackend.repository.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.auth.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByStudentNumberIgnoreCase(String studentNumber);

    /**
     * Matches a phone number regardless of how it was typed at signup. Both
     * sides are reduced to digits, an international {@code 00} prefix is
     * dropped, a trunk zero after the {@code 256} country code is removed, and
     * national numbers (mobile {@code 07...} and landlines {@code 0...}) are
     * given their {@code 256} country code. So {@code +256 700 000000},
     * {@code +256 (0)700 000000}, {@code 00256 700 000000},
     * {@code 0700-000000}, {@code 256700000000} and {@code 041 234567} all
     * resolve to the same stored form. The stored value is left untouched, so
     * existing rows match without a data rewrite.
     */
    @Query(value = """
            select *
            from user_profiles
            where normalize_phone_number(phone) = normalize_phone_number(:phone)
            """, nativeQuery = true)
    List<UserProfile> findAllByNormalizedPhone(@Param("phone") String phone);
}
