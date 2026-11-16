package org.unipod.unipoderpbackend.repository.operations;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.unipod.unipoderpbackend.model.operations.MediaContract;

public interface MediaContractRepository extends JpaRepository<MediaContract, UUID> {
    List<MediaContract> findAllByOrderByEventDateDescCreatedAtDesc();
    @Query(value = "select nextval('media_contract_code_seq')", nativeQuery = true)
    long nextCodeSeq();
}

