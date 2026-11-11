package org.unipod.unipoderpbackend.repository.partner;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.unipod.unipoderpbackend.model.partner.Partner;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    // Matches ix_partners_renewal_date_name: soonest renewal first, undated partners last.
    @Query("""
            select p from Partner p
            order by p.renewalDate asc nulls last, lower(p.name) asc
            """)
    List<Partner> findAllByRenewalOrder();
}
