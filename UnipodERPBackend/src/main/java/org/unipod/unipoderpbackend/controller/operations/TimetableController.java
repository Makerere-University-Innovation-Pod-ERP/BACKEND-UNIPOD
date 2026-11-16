package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.unipod.unipoderpbackend.dto.operations.TimetableBlockRequest;
import org.unipod.unipoderpbackend.dto.operations.TimetableBlockResponse;
import org.unipod.unipoderpbackend.service.operations.TimetableService;

@RestController
@RequestMapping("/timetable/blocks")
public class TimetableController {
    private final TimetableService timetable;
    public TimetableController(TimetableService timetable) { this.timetable = timetable; }

    @GetMapping
    public List<TimetableBlockResponse> list(
            @RequestParam(required = false) UUID facility_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return timetable.list(facility_id, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR')")
    public List<TimetableBlockResponse> create(@Valid @RequestBody TimetableBlockRequest request, Authentication auth) {
        return timetable.create(request, auth.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR')")
    public void delete(@PathVariable UUID id) { timetable.delete(id); }
}

