package org.unipod.unipoderpbackend.controller.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.booking.AddAttendeeRequest;
import org.unipod.unipoderpbackend.dto.booking.AttendeeResponse;
import org.unipod.unipoderpbackend.dto.booking.CancelTextileBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.CreateLabBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.CreateTextileBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.LabBookingResponse;
import org.unipod.unipoderpbackend.dto.booking.LabBookingStatus;
import org.unipod.unipoderpbackend.dto.booking.LabCalendarResponse;
import org.unipod.unipoderpbackend.dto.booking.SetAttendanceRequest;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingResponse;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingStatus;
import org.unipod.unipoderpbackend.dto.booking.TextileCalendarResponse;
import org.unipod.unipoderpbackend.dto.booking.TextileKind;
import org.unipod.unipoderpbackend.dto.booking.TimetableEntryResponse;
import org.unipod.unipoderpbackend.dto.media.MediaBoardOverview;
import org.unipod.unipoderpbackend.dto.media.MediaBoardRequest;
import org.unipod.unipoderpbackend.dto.media.UpdateMediaDetailsRequest;
import org.unipod.unipoderpbackend.dto.media.UpdateMediaStatusRequest;
import org.unipod.unipoderpbackend.dto.training.BootcampAttendance;
import org.unipod.unipoderpbackend.dto.training.BootcampListResponse;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.dto.training.CreateBootcampRequest;
import org.unipod.unipoderpbackend.dto.training.EnrollBootcampRequest;
import org.unipod.unipoderpbackend.dto.training.SaveBootcampAttendanceRequest;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;
import org.yaml.snakeyaml.Yaml;

/**
 * Keeps the documented media board, lab, textile and bootcamp contract (openapi.yaml at the repository root)
 * equal to the code: the operations with their roles and error codes, the enums, and the fields of every schema.
 */
class DepartmentOpenApiContractTest {
    private static final Set<String> READ = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR",
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER",
            "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER");
    private static final Set<String> MANAGE = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR");
    private static final Set<String> WRITE_ERRORS = Set.of("400", "401", "403", "404", "409", "422");

    private static Map<String, Object> spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("..", "openapi.yaml"), StandardCharsets.UTF_8)) {
            spec = new Yaml().load(reader);
        }
    }

    @Test
    void documentsEveryMediaOperation() {
        assertOperation("/operations/media/overview", "get", "getMediaBoardOverview", "Media", READ, Set.of("200", "401", "403"));
        assertOperation("/operations/media/requests", "get", "listMediaBoardRequests", "Media", READ, Set.of("200", "401", "403"));
        assertOperation("/operations/media/requests/{id}/status", "patch", "changeMediaRequestStatus", "Media", MANAGE,
                with("200"));
        assertOperation("/operations/media/requests/{id}/details", "patch", "updateMediaRequestDetails", "Media", MANAGE,
                with("200"));
    }

    @Test
    void documentsEveryLabAndBootcampOperation() {
        assertOperation("/telecoms/lab-bookings", "get", "getLabCalendar", "Telecoms", READ,
                Set.of("200", "400", "401", "403", "422"));
        assertOperation("/telecoms/lab-bookings", "post", "createLabBooking", "Telecoms", MANAGE,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/telecoms/bootcamps", "get", "listBootcamps", "Telecoms", READ, Set.of("200", "401", "403"));
        assertOperation("/telecoms/bootcamps", "post", "createBootcamp", "Telecoms", MANAGE,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/telecoms/bootcamps/{id}/enroll", "post", "enrollInBootcamp", "Telecoms", MANAGE, with("200"));
        assertOperation("/telecoms/bootcamps/{id}/attendance", "post", "saveBootcampAttendance", "Telecoms", MANAGE,
                with("200"));
    }

    @Test
    void documentsEveryTextileOperation() {
        assertOperation("/textile/bookings", "get", "getTextileCalendar", "Textile", READ,
                Set.of("200", "400", "401", "403", "422"));
        assertOperation("/textile/bookings", "post", "createTextileBooking", "Textile", MANAGE,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/textile/bookings/{id}/cancel", "post", "cancelTextileBooking", "Textile", MANAGE, with("200"));
        assertOperation("/textile/bookings/{id}/attendees", "post", "addTextileAttendee", "Textile", MANAGE, with("201"));
        assertOperation("/textile/bookings/{id}/attendees/{attendeeId}", "put", "setTextileAttendance", "Textile", MANAGE,
                with("200"));
    }

    @Test
    void enumsMatchTheCode() {
        assertEquals(names(MediaBoardStatus.values()), enumValues("MediaBoardStatus"));
        assertEquals(names(PaymentStatus.values()), enumValues("PaymentStatus"));
        assertEquals(names(TextileKind.values()), propertyEnum("TextileBooking", "kind"));
        assertEquals(names(TextileBookingStatus.values()), propertyEnum("TextileBooking", "status"));
        assertEquals(names(LabBookingStatus.values()), propertyEnum("LabBooking", "status"));
        assertEquals(names(BootcampAttendance.values()), propertyEnum("SaveBootcampAttendanceRequest", "attendance", "status"));
    }

    @Test
    void schemaFieldsMatchTheResponseAndRequestRecords() {
        assertFields("MediaBoardRequest", MediaBoardRequest.class);
        assertFields("MediaBoardOverview", MediaBoardOverview.class);
        assertFields("UpdateMediaStatusRequest", UpdateMediaStatusRequest.class);
        assertFields("UpdateMediaDetailsRequest", UpdateMediaDetailsRequest.class);
        assertFields("TimetableEntry", TimetableEntryResponse.class);
        assertFields("LabBooking", LabBookingResponse.class);
        assertFields("LabCalendar", LabCalendarResponse.class);
        assertFields("CreateLabBookingRequest", CreateLabBookingRequest.class);
        assertFields("TextileAttendee", AttendeeResponse.class);
        assertFields("TextileBooking", TextileBookingResponse.class);
        assertFields("TextileCalendar", TextileCalendarResponse.class);
        assertFields("CreateTextileBookingRequest", CreateTextileBookingRequest.class);
        assertFields("CancelTextileBookingRequest", CancelTextileBookingRequest.class);
        assertFields("AddAttendeeRequest", AddAttendeeRequest.class);
        assertFields("SetAttendanceRequest", SetAttendanceRequest.class);
        assertFields("Bootcamp", BootcampResponse.class);
        assertFields("BootcampList", BootcampListResponse.class);
        assertFields("CreateBootcampRequest", CreateBootcampRequest.class);
        assertFields("EnrollBootcampRequest", EnrollBootcampRequest.class);
        assertFields("SaveBootcampAttendanceRequest", SaveBootcampAttendanceRequest.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedBootcampObjectsMatchTheCode() {
        Map<String, Object> bootcamp = (Map<String, Object>) properties("Bootcamp");
        Map<String, Object> enrolled = (Map<String, Object>) ((Map<String, Object>) bootcamp.get("enrolled")).get("items");
        Map<String, Object> attendance = (Map<String, Object>) ((Map<String, Object>) bootcamp.get("attendance")).get("items");
        assertEquals(components(BootcampResponse.Participant.class),
                new TreeSet<>(((Map<String, Object>) enrolled.get("properties")).keySet()));
        assertEquals(components(BootcampResponse.AttendanceEntry.class),
                new TreeSet<>(((Map<String, Object>) attendance.get("properties")).keySet()));
        Map<String, Object> request = (Map<String, Object>) ((Map<String, Object>) properties("SaveBootcampAttendanceRequest")
                .get("attendance")).get("items");
        assertEquals(components(SaveBootcampAttendanceRequest.Entry.class),
                new TreeSet<>(((Map<String, Object>) request.get("properties")).keySet()));
    }

    // ----- helpers

    private static Set<String> with(String success) {
        Set<String> codes = new TreeSet<>(WRITE_ERRORS);
        codes.add(success);
        return codes;
    }

    @SuppressWarnings("unchecked")
    private static void assertOperation(
            String path, String method, String operationId, String tag, Set<String> roles, Set<String> responses) {
        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                spec.get("paths")).get(path)).get(method);
        assertEquals(operationId, operation.get("operationId"), path + " " + method);
        assertEquals(List.of(tag), operation.get("tags"), path + " " + method);
        assertEquals(roles, Set.copyOf((List<String>) operation.get("x-required-roles")), path + " " + method);
        assertEquals(responses, ((Map<String, Object>) operation.get("responses")).keySet(), path + " " + method);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) spec.get("components"))
                .get("schemas")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(String name) {
        return (Map<String, Object>) schema(name).get("properties");
    }

    /** Every record component is a documented property, and every documented property is a component. */
    private static void assertFields(String schemaName, Class<? extends Record> type) {
        assertEquals(components(type), new TreeSet<>(properties(schemaName).keySet()), schemaName);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> enumValues(String name) {
        return new TreeSet<>((List<String>) schema(name).get("enum"));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> propertyEnum(String schemaName, String... path) {
        Map<String, Object> node = properties(schemaName);
        Map<String, Object> property = (Map<String, Object>) node.get(path[0]);
        for (int index = 1; index < path.length; index++) {
            property = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) property.get("items"))
                    .get("properties")).get(path[index]);
        }
        return new TreeSet<>((List<String>) property.get("enum"));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
