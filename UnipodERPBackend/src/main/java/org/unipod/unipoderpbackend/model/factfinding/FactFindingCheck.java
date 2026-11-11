package org.unipod.unipoderpbackend.model.factfinding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "fact_finding_checks")
public class FactFindingCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brief_id", nullable = false)
    private Long briefId;

    @Column(nullable = false)
    private int round = 1;

    @Column(name = "officer_id", nullable = false)
    private Long officerId;

    @Column(name = "title_clear")
    private Boolean titleClear;

    @Column(name = "title_note", length = 1000)
    private String titleNote;

    @Column(name = "purpose_clear")
    private Boolean purposeClear;

    @Column(name = "purpose_note", length = 1000)
    private String purposeNote;

    @Column(name = "evidence_enough")
    private Boolean evidenceEnough;

    @Column(name = "evidence_note", length = 1000)
    private String evidenceNote;

    @Column(name = "relevant_to_hub")
    private Boolean relevantToHub;

    @Column(name = "relevant_note", length = 1000)
    private String relevantNote;

    @Column(name = "missing_items", length = 2000)
    private String missingItems;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FactFindingResult result;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected FactFindingCheck() {
    }

    public FactFindingCheck(Long briefId, int round, Long officerId) {
        this.briefId = briefId;
        this.round = round;
        this.officerId = officerId;
    }

    @PrePersist
    void markCreated() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBriefId() {
        return briefId;
    }

    public int getRound() {
        return round;
    }

    public Long getOfficerId() {
        return officerId;
    }

    public Boolean getTitleClear() {
        return titleClear;
    }

    public void setTitleClear(Boolean titleClear) {
        this.titleClear = titleClear;
    }

    public String getTitleNote() {
        return titleNote;
    }

    public void setTitleNote(String titleNote) {
        this.titleNote = titleNote;
    }

    public Boolean getPurposeClear() {
        return purposeClear;
    }

    public void setPurposeClear(Boolean purposeClear) {
        this.purposeClear = purposeClear;
    }

    public String getPurposeNote() {
        return purposeNote;
    }

    public void setPurposeNote(String purposeNote) {
        this.purposeNote = purposeNote;
    }

    public Boolean getEvidenceEnough() {
        return evidenceEnough;
    }

    public void setEvidenceEnough(Boolean evidenceEnough) {
        this.evidenceEnough = evidenceEnough;
    }

    public String getEvidenceNote() {
        return evidenceNote;
    }

    public void setEvidenceNote(String evidenceNote) {
        this.evidenceNote = evidenceNote;
    }

    public Boolean getRelevantToHub() {
        return relevantToHub;
    }

    public void setRelevantToHub(Boolean relevantToHub) {
        this.relevantToHub = relevantToHub;
    }

    public String getRelevantNote() {
        return relevantNote;
    }

    public void setRelevantNote(String relevantNote) {
        this.relevantNote = relevantNote;
    }

    public String getMissingItems() {
        return missingItems;
    }

    public void setMissingItems(String missingItems) {
        this.missingItems = missingItems;
    }

    public FactFindingResult getResult() {
        return result;
    }

    public void setResult(FactFindingResult result) {
        this.result = result;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
