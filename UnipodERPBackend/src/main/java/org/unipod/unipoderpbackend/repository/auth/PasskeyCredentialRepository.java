package org.unipod.unipoderpbackend.repository.auth;

import java.time.Instant;
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
    @Query(value = """
            insert into passkey_credentials (
                credential_id, user_id, public_key_cose, signature_count, created_at
            ) values (
                :credentialId, :userId, :publicKeyCose, :signatureCount, :createdAt
            )
            on conflict (credential_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("credentialId") String credentialId,
            @Param("userId") UUID userId,
            @Param("publicKeyCose") byte[] publicKeyCose,
            @Param("signatureCount") long signatureCount,
            @Param("createdAt")             Instant createdAt
    );

    @Modifying
    @Query("""
            update PasskeyCredential credential
            set credential.signatureCount = :signatureCount
            where credential.credentialId = :credentialId
              and (
                  credential.signatureCount < :signatureCount
                  or (credential.signatureCount = 0 and :signatureCount = 0)
              )
            """)
    int updateSignatureCount(
            @Param("credentialId") String credentialId,
            @Param("signatureCount") long signatureCount
    );
}
