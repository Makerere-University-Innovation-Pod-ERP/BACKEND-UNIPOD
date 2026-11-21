package org.unipod.unipoderpbackend.dto.equipment;

import java.util.List;

/** The `{items: [...]}` wrapper the CNC, telecoms and textile screens expect around their lists. */
public record ItemsResponse<T>(List<T> items) {
}
