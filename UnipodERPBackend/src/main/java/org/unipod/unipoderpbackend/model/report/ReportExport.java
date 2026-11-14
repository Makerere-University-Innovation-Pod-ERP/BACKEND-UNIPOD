package org.unipod.unipoderpbackend.model.report;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "report_exports")
public class ReportExport {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportType report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExportFormat format;

    @Column(name = "requested_by", nullable = false, length = 254)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;

    protected ReportExport() {
    }

    public ReportExport(UUID id, ReportType report, ExportFormat format, String requestedBy,
                        String fileName, byte[] content, Instant now) {
        this.id = id;
        this.report = report;
        this.format = format;
        this.requestedBy = requestedBy;
        this.fileName = fileName;
        this.content = content;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public ReportType getReport() {
        return report;
    }

    public ExportFormat getFormat() {
        return format;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }
}
