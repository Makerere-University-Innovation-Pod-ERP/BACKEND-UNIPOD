package org.unipod.unipoderpbackend.dto.improvement;

import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;

public record RequestedChangeItem(int position, String text) {
    public static RequestedChangeItem from(RefinementRequestedChange change) {
        return new RequestedChangeItem(change.getPosition(), change.getText());
    }
}
