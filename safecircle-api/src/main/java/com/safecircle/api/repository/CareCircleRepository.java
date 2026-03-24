package com.safecircle.api.repository;

import com.safecircle.common.model.CareCircleMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareCircleRepository extends JpaRepository<CareCircleMember, UUID> {

    /**
     * The most critical query in the care circle domain.
     * Called by EscalationService when building the notification list.
     *
     * JOIN FETCH CONCEPT (revisited):
     * We fetch the contactUser in the same query using JOIN FETCH.
     * Without this, accessing ccm.getContactUser() inside the loop in
     * EscalationService would fire one extra SELECT per member —
     * the classic N+1 problem. With JOIN FETCH, it's always one query total.
     *
     * We filter status = 'ACTIVE' — PENDING and DECLINED contacts never
     * receive alerts. A contact must explicitly accept before being included.
     */
    @Query("""
        SELECT ccm FROM CareCircleMember ccm
        JOIN FETCH ccm.contactUser
        WHERE ccm.monitoredUser.id = :userId
          AND ccm.status = 'ACTIVE'
        ORDER BY ccm.priorityOrder ASC
        """)
    List<CareCircleMember> findActiveByMonitoredUserIdOrderByPriority(
        @Param("userId") UUID userId
    );

    /** All members (any status) — used for the GET /care-circle list endpoint. */
    @Query("""
        SELECT ccm FROM CareCircleMember ccm
        JOIN FETCH ccm.contactUser
        WHERE ccm.monitoredUser.id = :userId
        ORDER BY ccm.priorityOrder ASC
        """)
    List<CareCircleMember> findAllByMonitoredUserIdOrderByPriority(
        @Param("userId") UUID userId
    );

    /** Used to check if a contact is already in this care circle before inviting. */
    boolean existsByMonitoredUserIdAndContactUserId(UUID monitoredUserId, UUID contactUserId);

    /**
     * Finds a specific member by ID, but only if it belongs to the given monitored user.
     * This is an AUTHORIZATION check built into the query — a user cannot fetch
     * or modify care circle members that belong to someone else.
     * Always use this instead of plain findById when serving user requests.
     */
    Optional<CareCircleMember> findByIdAndMonitoredUserId(UUID id, UUID monitoredUserId);

    /**
     * Used by the "leave care circle" flow where the CONTACT removes themselves.
     * A contact can find their own membership by their contact user ID.
     */
    Optional<CareCircleMember> findByIdAndContactUserId(UUID id, UUID contactUserId);

    /** Used to find all circles a user is a contact in — for the "invites" screen. */
    List<CareCircleMember> findByContactUserIdAndStatus(UUID contactUserId, String status);
}