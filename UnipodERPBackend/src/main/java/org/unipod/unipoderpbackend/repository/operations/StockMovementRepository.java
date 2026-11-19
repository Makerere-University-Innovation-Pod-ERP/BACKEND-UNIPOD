package org.unipod.unipoderpbackend.repository.operations;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.operations.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
}
