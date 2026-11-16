package org.unipod.unipoderpbackend.dto.operations;

import java.util.List;
import java.util.UUID;

public record MediaDeliverRequest(List<String> delivery_links, List<UUID> file_ids, String note_to_submitter) {}

