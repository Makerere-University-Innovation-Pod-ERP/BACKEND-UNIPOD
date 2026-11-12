package org.unipod.unipoderpbackend.repository.partner;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    // Matches ix_partners_renewal_date_name: soonest renewal first, undated partners last.
    @Query("""
            select p from Partner p
            order by p.renewalDate asc nulls last, lower(p.name) asc
            """)
    List<Partner> findAllByRenewalOrder();

    // Same expression as uq_partners_name so the lookup uses that index.
    @Query("select count(p) > 0 from Partner p where lower(p.name) = lower(:name)")
    boolean existsByNameIgnoringCase(@Param("name") String name);

    @Query("select count(p) > 0 from Partner p where lower(p.name) = lower(:name) and p.id <> :id")
    boolean existsByNameIgnoringCaseExcluding(@Param("name") String name, @Param("id") UUID id);

    /**
     * Claims the right to notify for {@code status} as worked out from {@code renewalDate}: returns
     * 1 for exactly one caller, even when several instances run the job. If the renewal date has
     * changed since the caller read it the claim fails, so no warning goes out for a superseded
     * date. A bulk update, so the row's version is untouched and a user's open edit is not turned
     * into a conflict by bookkeeping.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Partner p set p.lastNotifiedStatus = :status
            where p.id = :id and p.renewalDate = :renewalDate
              and (p.lastNotifiedStatus is null or p.lastNotifiedStatus <> :status)
            """)
    int claimNotification(
            @Param("id") UUID id, @Param("status") PartnerStatus status, @Param("renewalDate") LocalDate renewalDate);
}
