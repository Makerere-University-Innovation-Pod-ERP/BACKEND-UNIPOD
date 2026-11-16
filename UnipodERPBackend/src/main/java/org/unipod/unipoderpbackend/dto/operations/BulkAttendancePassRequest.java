package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record BulkAttendancePassRequest(@NotEmpty List<UUID> user_ids) {}

