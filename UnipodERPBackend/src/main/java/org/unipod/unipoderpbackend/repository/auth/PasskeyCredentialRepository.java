package org.unipod.unipoderpbackend.repository.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.auth.PasskeyCredential;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, String> {
    List<PasskeyCredential> findAllByUserId(UUID userId);

    @Modifying
    @Query("""
            update PasskeyCredential credential
            set credential.signatureCount = :signatureCount
            where credential.credentialId = :credentialId
            """)
    int updateSignatureCount(
            @Param("credentialId") String credentialId,
            @Param("signatureCount") long signatureCount
    );
}
