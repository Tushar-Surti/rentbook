package com.rentbook.condition;

import com.rentbook.common.ApiException;
import com.rentbook.document.DocumentService;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseService;
import com.rentbook.user.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Move-in and move-out condition reports. The landlord walks through the home line by line, with photos, and
 * sends the report; the tenant reads the same report, adds their own notes and photos, and confirms it. The
 * move-out report starts from the move-in one, so every line says whether it got worse, which is what a deposit
 * deduction should rest on. Tenants never see a draft; strangers to the lease get "not found".
 */
@Service
public class ConditionReports {

    private final ConditionReportRepository reports;
    private final ConditionItemRepository lines;
    private final LeaseService leases;
    private final DocumentService documents;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ConditionReports(ConditionReportRepository reports, ConditionItemRepository lines, LeaseService leases,
                     DocumentService documents, ApplicationEventPublisher events, Clock clock) {
        this.reports = reports;
        this.lines = lines;
        this.leases = leases;
        this.documents = documents;
        this.events = events;
        this.clock = clock;
    }

    /** A report in a lease's list: {@code worse} counts move-out lines in a worse state than at move-in. */
    public record Summary(UUID id, ConditionReport.Kind kind, ConditionReport.Status status, Instant sentAt,
                          Instant confirmedAt, int lines, int worse) {
    }

    public record Photo(UUID id, String filename, String url, boolean byTenant, boolean mine) {
    }

    /**
     * {@code atMoveIn} is the same line's condition on the move-in report, on a move-out report only; null when
     * the move-in report didn't have it.
     */
    public record Line(UUID id, String area, String item, ConditionItem.Condition condition, String note,
                       String tenantNote, List<Photo> photos, ConditionItem.Condition atMoveIn, boolean worse) {
    }

    /** {@code comparedWithMoveIn} is true on a move-out report when there's a sent move-in report to compare. */
    public record ReportView(UUID id, ConditionReport.Kind kind, ConditionReport.Status status, Instant createdAt,
                             Instant sentAt, Instant confirmedAt, LeaseService.LeaseView lease,
                             boolean comparedWithMoveIn, int worse, List<Line> lines) {
    }

    /** A report went to the tenant, or came back confirmed; {@link ConditionNotifications} tells the other side. */
    public record ReportChanged(UUID reportId, ConditionReport.Kind kind, ConditionReport.Status status,
                                LeaseService.LeaseView lease, int worse, int tenantNotes) {
    }

    @Transactional(readOnly = true)
    public List<Summary> list(UUID leaseId, UUID userId, Role role) {
        leases.require(leaseId, userId, role);
        List<ConditionReport> found = reports.findByLeaseIdOrderByCreatedAtAsc(leaseId).stream()
                .filter(report -> role == Role.LANDLORD || !report.isDraft())
                .toList();
        Map<UUID, List<ConditionItem>> byReport = lines.findByReportIdInOrderByPositionAsc(
                        found.stream().map(ConditionReport::getId).toList()).stream()
                .collect(Collectors.groupingBy(ConditionItem::getReportId));
        return found.stream().map(report -> {
            List<ConditionItem> own = byReport.getOrDefault(report.getId(), List.of());
            Map<String, ConditionItem.Condition> before = moveInConditions(report);
            return new Summary(report.getId(), report.getKind(), report.getStatus(), report.getSentAt(),
                    report.getConfirmedAt(), own.size(), worse(own, before));
        }).toList();
    }

    @Transactional(readOnly = true)
    public ReportView view(UUID reportId, UUID userId, Role role) {
        Owned owned = owned(reportId, userId, role);
        return view(owned.report(), owned.lease(), userId);
    }

    /**
     * Starts a report as a draft. A move-in report starts from the usual lines for the kind of home; a move-out
     * report starts from the move-in report's lines and conditions, so only what changed needs touching.
     */
    @Transactional
    public ReportView create(UUID landlordId, UUID leaseId, ConditionReport.Kind kind) {
        Lease lease = leases.require(leaseId, landlordId, Role.LANDLORD);
        LeaseService.LeaseView leaseView = leases.view(leaseId, landlordId, Role.LANDLORD);
        if (kind == ConditionReport.Kind.MOVE_IN && lease.getStatus() == Lease.Status.ENDED) {
            throw ApiException.conflict("lease_ended", "This lease has ended. Start the move-out report instead.");
        }
        if (kind == ConditionReport.Kind.MOVE_OUT && lease.getStatus() == Lease.Status.ACTIVE) {
            throw ApiException.conflict("not_moving_out", "Set %s's last day first; the move-out report is for the day they leave."
                    .formatted(leaseView.tenant().fullName()));
        }
        if (reports.findByLeaseIdAndKind(leaseId, kind).isPresent()) {
            throw ApiException.conflict("report_exists", kind == ConditionReport.Kind.MOVE_IN
                    ? "This lease already has a move-in report." : "This lease already has a move-out report.");
        }
        ConditionReport report = reports.saveAndFlush(new ConditionReport(leaseId, kind));
        List<ConditionItem> moveIn = kind == ConditionReport.Kind.MOVE_OUT
                ? reports.findByLeaseIdAndKind(leaseId, ConditionReport.Kind.MOVE_IN)
                .map(found -> lines.findByReportIdOrderByPositionAsc(found.getId())).orElse(List.of())
                : List.of();
        if (moveIn.isEmpty()) {
            List<Checklists.Line> start = Checklists.forUnit(leaseView.unit().kind());
            for (int index = 0; index < start.size(); index++) {
                lines.save(new ConditionItem(report.getId(), index + 1, start.get(index).area(),
                        start.get(index).item(), ConditionItem.Condition.GOOD));
            }
        } else {
            for (ConditionItem line : moveIn) {
                lines.save(new ConditionItem(report.getId(), line.getPosition(), line.getArea(), line.getItem(),
                        line.getCondition()));
            }
        }
        lines.flush();
        return view(report, lease, landlordId);
    }

    /** A draft can be thrown away, with its photos; a sent report is part of the lease's record. */
    @Transactional
    public void discard(UUID landlordId, UUID reportId) {
        Owned owned = owned(reportId, landlordId, Role.LANDLORD);
        requireDraft(owned.report());
        List<ConditionItem> own = lines.findByReportIdOrderByPositionAsc(reportId);
        documents.discardConditionPhotos(own.stream().map(ConditionItem::getId).toList());
        lines.deleteAll(own);
        lines.flush();
        reports.delete(owned.report());
    }

    @Transactional
    public ReportView addLine(UUID landlordId, UUID reportId, String area, String item) {
        Owned owned = owned(reportId, landlordId, Role.LANDLORD);
        requireDraft(owned.report());
        lines.saveAndFlush(new ConditionItem(reportId, lines.lastPosition(reportId) + 1, area, item,
                ConditionItem.Condition.GOOD));
        return view(owned.report(), owned.lease(), landlordId);
    }

    @Transactional
    public Line updateLine(UUID landlordId, UUID lineId, ConditionItem.Condition condition, String note) {
        ConditionItem line = lines.findById(lineId).orElseThrow(() -> ApiException.notFound("Line"));
        Owned owned = owned(line.getReportId(), landlordId, Role.LANDLORD);
        requireDraft(owned.report());
        line.record(condition, note);
        lines.flush();
        return view(owned.report(), owned.lease(), landlordId).lines().stream()
                .filter(found -> found.id().equals(lineId)).findFirst().orElseThrow();
    }

    @Transactional
    public ReportView removeLine(UUID landlordId, UUID lineId) {
        ConditionItem line = lines.findById(lineId).orElseThrow(() -> ApiException.notFound("Line"));
        Owned owned = owned(line.getReportId(), landlordId, Role.LANDLORD);
        requireDraft(owned.report());
        documents.discardConditionPhotos(List.of(lineId));
        lines.delete(line);
        lines.flush();
        return view(owned.report(), owned.lease(), landlordId);
    }

    /** From here the tenant can read it, and it can't change on the landlord's side. */
    @Transactional
    public ReportView send(UUID landlordId, UUID reportId) {
        Owned owned = owned(reportId, landlordId, Role.LANDLORD);
        requireDraft(owned.report());
        if (lines.findByReportIdOrderByPositionAsc(reportId).isEmpty()) {
            throw ApiException.badRequest("empty_report", "Add at least one line before sending the report.");
        }
        owned.report().send(clock.instant());
        reports.flush();
        ReportView view = view(owned.report(), owned.lease(), landlordId);
        events.publishEvent(new ReportChanged(reportId, view.kind(), view.status(), view.lease(), view.worse(), 0));
        return view;
    }

    /**
     * The tenant agrees this is the record, with their own note on any line they see differently. Photos they
     * added while reading it stay with it.
     */
    @Transactional
    public ReportView confirm(UUID tenantId, UUID reportId, Map<UUID, String> notes) {
        Owned owned = owned(reportId, tenantId, Role.TENANT);
        if (owned.report().getStatus() != ConditionReport.Status.SENT) {
            throw ApiException.conflict("report_confirmed", "You've already confirmed this report.");
        }
        Map<UUID, ConditionItem> own = lines.findByReportIdOrderByPositionAsc(reportId).stream()
                .collect(Collectors.toMap(ConditionItem::getId, Function.identity()));
        if (!own.keySet().containsAll(notes.keySet())) {
            throw ApiException.badRequest("notes", "One of those notes is for a line that isn't on this report.");
        }
        notes.forEach((lineId, note) -> own.get(lineId).noteFromTenant(note));
        owned.report().confirm(clock.instant());
        reports.flush();
        ReportView view = view(owned.report(), owned.lease(), tenantId);
        int noted = (int) view.lines().stream().filter(line -> line.tenantNote() != null).count();
        events.publishEvent(new ReportChanged(reportId, view.kind(), view.status(), view.lease(), view.worse(), noted));
        return view;
    }

    /** The landlord adds photos while writing the report; the tenant, while reading it before confirming. */
    @Transactional
    public DocumentService.UploadTicket startPhoto(UUID userId, Role role, UUID lineId, String filename,
                                                   String contentType, long sizeBytes) {
        ConditionItem line = lines.findById(lineId).orElseThrow(() -> ApiException.notFound("Line"));
        Owned owned = owned(line.getReportId(), userId, role);
        requirePhotosOpen(owned.report(), role);
        return documents.startConditionPhoto(owned.lease(), userId, lineId, filename, contentType, sizeBytes);
    }

    /** Only whoever took a photo can take it off, and only while they could still add one. */
    @Transactional
    public void removePhoto(UUID userId, Role role, UUID photoId) {
        UUID lineId = documents.conditionItemOf(photoId, userId);
        ConditionItem line = Optional.ofNullable(lineId).flatMap(lines::findById)
                .orElseThrow(() -> ApiException.notFound("Photo"));
        Owned owned = owned(line.getReportId(), userId, role);
        requirePhotosOpen(owned.report(), role);
        documents.discardConditionPhoto(photoId);
    }

    private record Owned(ConditionReport report, Lease lease) {
    }

    private Owned owned(UUID reportId, UUID userId, Role role) {
        ConditionReport report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
        Lease lease;
        try {
            lease = leases.require(report.getLeaseId(), userId, role);
        } catch (ApiException stranger) {
            throw ApiException.notFound("Report");
        }
        if (role != Role.LANDLORD && report.isDraft()) {
            throw ApiException.notFound("Report");
        }
        return new Owned(report, lease);
    }

    private static void requireDraft(ConditionReport report) {
        if (!report.isDraft()) {
            throw ApiException.conflict("report_sent", "This report has been sent, so it stays as it is.");
        }
    }

    private static void requirePhotosOpen(ConditionReport report, Role role) {
        if (role == Role.LANDLORD) {
            requireDraft(report);
        } else if (report.getStatus() != ConditionReport.Status.SENT) {
            throw ApiException.conflict("report_confirmed", "You've confirmed this report, so it stays as it is.");
        }
    }

    /** The move-in report's conditions by line, for a move-out report; empty when there's nothing to compare. */
    private Map<String, ConditionItem.Condition> moveInConditions(ConditionReport report) {
        if (report.getKind() != ConditionReport.Kind.MOVE_OUT) {
            return Map.of();
        }
        return reports.findByLeaseIdAndKind(report.getLeaseId(), ConditionReport.Kind.MOVE_IN)
                .filter(moveIn -> !moveIn.isDraft())
                .map(moveIn -> lines.findByReportIdOrderByPositionAsc(moveIn.getId()).stream()
                        .collect(Collectors.toMap(ConditionItem::key, ConditionItem::getCondition,
                                (first, second) -> first)))
                .orElse(Map.of());
    }

    private static int worse(List<ConditionItem> own, Map<String, ConditionItem.Condition> before) {
        return (int) own.stream().filter(line -> {
            ConditionItem.Condition was = before.get(line.key());
            return was != null && line.getCondition().worseThan(was);
        }).count();
    }

    private ReportView view(ConditionReport report, Lease lease, UUID viewerId) {
        LeaseService.LeaseView leaseView = leases.view(lease.getId(), lease.getLandlordId(), Role.LANDLORD);
        List<ConditionItem> own = lines.findByReportIdOrderByPositionAsc(report.getId());
        Map<UUID, List<DocumentService.ConditionPhoto>> photos = documents.conditionPhotos(
                own.stream().map(ConditionItem::getId).toList());
        Map<String, ConditionItem.Condition> before = moveInConditions(report);
        boolean compared = !before.isEmpty();
        List<Line> view = own.stream().map(line -> {
            ConditionItem.Condition was = compared ? before.get(line.key()) : null;
            return new Line(line.getId(), line.getArea(), line.getItem(), line.getCondition(), line.getNote(),
                    line.getTenantNote(), photos.getOrDefault(line.getId(), List.of()).stream()
                    .map(photo -> new Photo(photo.id(), photo.filename(), photo.url(),
                            photo.uploadedBy().equals(lease.getTenantId()), photo.uploadedBy().equals(viewerId)))
                    .toList(), was, was != null && line.getCondition().worseThan(was));
        }).toList();
        return new ReportView(report.getId(), report.getKind(), report.getStatus(), report.getCreatedAt(),
                report.getSentAt(), report.getConfirmedAt(), leaseView, compared, worse(own, before), view);
    }
}
