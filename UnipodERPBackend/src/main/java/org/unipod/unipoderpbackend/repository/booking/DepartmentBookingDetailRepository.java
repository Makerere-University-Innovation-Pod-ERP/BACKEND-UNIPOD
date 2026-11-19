package org.unipod.unipoderpbackend.repository.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

public interface DepartmentBookingDetailRepository extends JpaRepository<DepartmentBookingDetail, UUID> {
    // Sign-ups and cancellations take this lock, so changes to one booking are applied one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DepartmentBookingDetail d where d.bookingId = :bookingId")
    Optional<DepartmentBookingDetail> findByIdForUpdate(@Param("bookingId") UUID bookingId);

    /**
     * The department's bookings that overlap the window, soonest first. Matches ix_bookings_facility_starts
     * and ix_department_booking_details_department.
     */
    @Query("""
            select b from Booking b, DepartmentBookingDetail d
            where d.bookingId = b.id
              and d.department = :department
              and b.startsAt < :to and b.endsAt > :from
              and b.status in :statuses
            order by b.startsAt asc, b.id asc
            """)
    List<Booking> findInWindow(
            @Param("department") DepartmentCode department,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("statuses") Collection<BookingStatus> statuses);

    /** The stored status of one department booking, read as a plain value so no stale entity is kept. */
    @Query("""
            select b.status from Booking b, DepartmentBookingDetail d
            where d.bookingId = b.id and d.department = :department and b.id = :id
            """)
    Optional<BookingStatus> findStatus(@Param("department") DepartmentCode department, @Param("id") UUID id);

    /** One department booking, only if it belongs to that department. */
    @Query("""
            select b from Booking b, DepartmentBookingDetail d
            where d.bookingId = b.id and d.department = :department and b.id = :id
            """)
    Optional<Booking> findBooking(@Param("department") DepartmentCode department, @Param("id") UUID id);
}
