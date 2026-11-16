package org.unipod.unipoderpbackend.model.improvement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One item in the list of changes asked for by a {@link RefinementRequest}. Immutable once created. */
@Entity
@Table(name = "refinement_requested_changes")
public class RefinementRequestedChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, updatable = false, length = 500)
    private String text;

    protected RefinementRequestedChange() {
    }

    public RefinementRequestedChange(Long requestId, int position, String text) {
        this.requestId = requestId;
        this.position = position;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }
}
