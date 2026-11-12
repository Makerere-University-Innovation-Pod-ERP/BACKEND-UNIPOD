package org.unipod.unipoderpbackend.repository.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.auth.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByStudentNumberIgnoreCase(String studentNumber);
}
