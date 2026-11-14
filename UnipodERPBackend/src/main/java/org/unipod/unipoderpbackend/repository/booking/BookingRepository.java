package org.unipod.unipoderpbackend.repository.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Override
    @EntityGraph(attributePaths = "facility")
    Optional<Booking> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "facility")
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "facility")
    Optional<Booking> findByIdAndRequesterId(UUID id, String requesterId);

    @EntityGraph(attributePaths = "facility")
    Page<Booking> findAllByRequesterIdOrderByStartsAtDescIdDesc(String requesterId, Pageable pageable);

    @EntityGraph(attributePaths = "facility")
    Page<Booking> findAllByOrderByStartsAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "facility")
    Page<Booking> findAllByStatusOrderByStartsAtDescIdDesc(BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "facility")
    Page<Booking> findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            Instant from,
            Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "facility")
    Page<Booking> findAllByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            BookingStatus status,
            Instant from,
            Instant to,
            Pageable pageable
    );

    @Query("""
            select (count(booking) > 0)
            from Booking booking
            where booking.facility.id = :facilityId
              and booking.status in :statuses
              and booking.startsAt < :endsAt
              and booking.endsAt > :startsAt
            """)
    boolean existsConflict(
            @Param("facilityId") UUID facilityId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Query("""
            select (count(booking) > 0)
            from Booking booking
            where booking.facility.id = :facilityId
              and booking.id <> :excludeId
              and booking.status in :statuses
              and booking.startsAt < :endsAt
              and booking.endsAt > :startsAt
            """)
    boolean existsConflictExcluding(
            @Param("facilityId") UUID facilityId,
            @Param("excludeId") UUID excludeId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Query(value = "select nextval('booking_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    @Query("""
            select count(booking)
            from Booking booking
            where booking.requesterId = :requesterId
              and booking.startsAt >= :now
              and booking.status in :statuses
            """)
    long countUpcoming(
            @Param("requesterId") String requesterId,
            @Param("now") Instant now,
            @Param("statuses") Collection<BookingStatus> statuses
    );
}
