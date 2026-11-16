package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "department_orders")
public class DepartmentOrder {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DepartmentCode department;
    @Column(name = "customer_id", nullable = false, length = 254)
    private String customerId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false)
    private int quantity = 1;
    @Column(name = "date_needed")
    private LocalDate dateNeeded;
    @Column(name = "accepted_rules_version", length = 32)
    private String acceptedRulesVersion;
    @Column(nullable = false, length = 40)
    private String stage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String specs = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String quote;
    @Column(name = "quote_submitted_at")
    private Instant quoteSubmittedAt;
    @Column(name = "quoted_by", length = 254)
    private String quotedBy;
    @Column(name = "invoice_id")
    private UUID invoiceId;
    @Column(name = "invoice_status", length = 32)
    private String invoiceStatus;
    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "materials_used", columnDefinition = "jsonb")
    private String materialsUsed;
    @Column(name = "machine_time_used", length = 255)
    private String machineTimeUsed;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_checklist", columnDefinition = "jsonb")
    private String qualityChecklist;
    @Column(name = "collection_signature", length = 255)
    private String collectionSignature;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stage_history", nullable = false, columnDefinition = "jsonb")
    private String stageHistory = "[]";
    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected DepartmentOrder() {}

    public DepartmentOrder(UUID id, String code, DepartmentCode department, String customerId, String title,
                           int quantity, LocalDate dateNeeded, String acceptedRulesVersion, String stage,
                           String specs, String stageHistory, String createdBy, Instant now) {
        this.id = id; this.code = code; this.department = department; this.customerId = customerId;
        this.title = title; this.quantity = quantity <= 0 ? 1 : quantity; this.dateNeeded = dateNeeded;
        this.acceptedRulesVersion = acceptedRulesVersion; this.stage = stage;
        this.specs = specs == null ? "{}" : specs; this.stageHistory = stageHistory == null ? "[]" : stageHistory;
        this.createdBy = createdBy; this.createdAt = now; this.updatedAt = now;
    }

    public void applyQuote(String quoteJson, String quotedBy, UUID invoiceId, String invoiceStatus, Instant now) {
        this.quote = quoteJson; this.quotedBy = quotedBy; this.quoteSubmittedAt = now;
        this.invoiceId = invoiceId; this.invoiceStatus = invoiceStatus; this.updatedAt = now;
    }
    public void moveStage(String stage, String stageHistory, Instant now) {
        this.stage = stage; this.stageHistory = stageHistory; this.updatedAt = now;
    }
    public void setCompletion(String materialsUsed, String machineTimeUsed, String qualityChecklist, String signature, Instant now) {
        this.materialsUsed = materialsUsed; this.machineTimeUsed = machineTimeUsed;
        this.qualityChecklist = qualityChecklist; this.collectionSignature = signature; this.updatedAt = now;
    }
    public void confirmPayment(String invoiceStatus, Instant now) {
        this.invoiceStatus = invoiceStatus; this.paymentConfirmedAt = now; this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public DepartmentCode getDepartment() { return department; }
    public String getCustomerId() { return customerId; }
    public String getTitle() { return title; }
    public int getQuantity() { return quantity; }
    public LocalDate getDateNeeded() { return dateNeeded; }
    public String getAcceptedRulesVersion() { return acceptedRulesVersion; }
    public String getStage() { return stage; }
    public String getSpecs() { return specs; }
    public String getQuote() { return quote; }
    public Instant getQuoteSubmittedAt() { return quoteSubmittedAt; }
    public String getQuotedBy() { return quotedBy; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getInvoiceStatus() { return invoiceStatus; }
    public String getMaterialsUsed() { return materialsUsed; }
    public String getMachineTimeUsed() { return machineTimeUsed; }
    public String getQualityChecklist() { return qualityChecklist; }
    public String getCollectionSignature() { return collectionSignature; }
    public String getStageHistory() { return stageHistory; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}

