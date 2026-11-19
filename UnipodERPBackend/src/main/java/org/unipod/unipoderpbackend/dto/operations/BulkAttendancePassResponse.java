package org.unipod.unipoderpbackend.dto.operations;

import java.time.Instant;
import java.util.UUID;

public record BulkAttendancePassResponse(UUID session_id, int passed_count, int eligible_unlocked_count) {}

