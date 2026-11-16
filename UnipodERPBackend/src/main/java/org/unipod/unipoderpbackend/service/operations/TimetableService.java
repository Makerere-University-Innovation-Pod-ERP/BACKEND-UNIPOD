package org.unipod.unipoderpbackend.service.operations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.TimetableBlockRequest;
import org.unipod.unipoderpbackend.dto.operations.TimetableBlockResponse;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.operations.TimetableBlock;
import org.unipod.unipoderpbackend.repository.operations.TimetableBlockRepository;

@Service
public class TimetableService {
    private final TimetableBlockRepository blocks;
    public TimetableService(TimetableBlockRepository blocks) { this.blocks = blocks; }

    @Transactional(readOnly = true)
    public List<TimetableBlockResponse> list(UUID facilityId, Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new InvalidOperationsRequestException("from and to are required and to must be after from.");
        }
        return blocks.findOverlapping(facilityId, from, to).stream().map(TimetableBlockResponse::from).toList();
    }

    @Transactional
    public List<TimetableBlockResponse> create(TimetableBlockRequest request, String actor) {
        if (request.starts_at() == null || request.ends_at() == null || !request.ends_at().isAfter(request.starts_at())) {
            throw new InvalidOperationsRequestException("ends_at must be after starts_at.");
        }
        Instant now = Instant.now();
        String rule = clean(request.recurrence_rule());
        List<TimetableBlock> toSave;
        if (rule != null && request.recurrence_until() != null) {
            toSave = generateRecurring(request, actor, now);
        } else {
            toSave = List.of(new TimetableBlock(UUID.randomUUID(), request.facility_id(), clean(request.department()),
                    request.title().trim(), request.starts_at(), request.ends_at(), rule, request.recurrence_until(),
                    clean(request.source()), actor, now));
        }
        blocks.saveAll(toSave);
        return toSave.stream().map(TimetableBlockResponse::from).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!blocks.existsById(id)) throw new OperationsNotFoundException("Timetable block not found.");
        blocks.deleteById(id);
    }

    private List<TimetableBlock> generateRecurring(TimetableBlockRequest request, String actor, Instant now) {
        long weeks = 0;
        Instant start = request.starts_at();
        Instant end = request.ends_at();
        long duration = ChronoUnit.MINUTES.between(start, end);
        java.util.ArrayList<TimetableBlock> out = new java.util.ArrayList<>();
        java.time.LocalDate until = request.recurrence_until();
        Instant cursorStart = start;
        Instant cursorEnd = end;
        // cap at 52 occurrences to be safe
        while (weeks < 52) {
            java.time.LocalDate d = cursorStart.atZone(java.time.ZoneId.of("Africa/Kampala")).toLocalDate();
            if (d.isAfter(until)) break;
            out.add(new TimetableBlock(UUID.randomUUID(), request.facility_id(), clean(request.department()),
                    request.title().trim(), cursorStart, cursorEnd, clean(request.recurrence_rule()),
                    request.recurrence_until(), clean(request.source()), actor, now));
            cursorStart = cursorStart.plus(7, ChronoUnit.DAYS);
            cursorEnd = cursorEnd.plus(7, ChronoUnit.DAYS);
            weeks++;
        }
        if (out.isEmpty()) out.add(new TimetableBlock(UUID.randomUUID(), request.facility_id(), clean(request.department()),
                request.title().trim(), start, end, clean(request.recurrence_rule()), request.recurrence_until(),
                clean(request.source()), actor, now));
        return out;
    }

    private static String clean(String v) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
}

