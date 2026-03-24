package com.safecircle.api.repository;

import com.safecircle.common.enums.AlertState;
import com.safecircle.common.model.AlertEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {

    /**
     * Checks if a user already has an unresolved alert. Used before creating
     * a new alert to avoid creating duplicates while one is already active.
     */
    boolean existsByMonitoredUserIdAndStateNot(UUID userId, AlertState state);

    /**
     * Finds an active (non-resolved) alert for a user — used by the
     * check-in endpoint to resolve the current alert.
     */
    Optional<AlertEvent> findByMonitoredUserIdAndStateNot(UUID userId, AlertState state);

    /**
     * PAGINATION CONCEPT:
     * Page<AlertEvent> + Pageable allows the caller to request "page 2 of 20 items".
     * Spring Data handles the LIMIT/OFFSET SQL automatically.
     *
     * The controller passes a Pageable object constructed from the
     * request's ?limit=20&offset=0 query parameters.
     *
     * JOIN FETCH CONCEPT:
     * Without JOIN FETCH, loading 20 AlertEvents and then accessing each one's
     * escalations list would fire 21 queries (1 for the list + 1 per row).
     * This is the "N+1 problem" — a very common performance bug.
     * JOIN FETCH loads AlertEvents AND their escalations in a single query.
     */
    @Query("""
        SELECT DISTINCT ae FROM AlertEvent ae
        LEFT JOIN FETCH ae.escalations
        WHERE ae.monitoredUser.id = :userId
        ORDER BY ae.triggeredAt DESC
        """)
    Page<AlertEvent> findByMonitoredUserIdOrderByTriggeredAtDesc(
        @Param("userId") UUID userId,
        Pageable pageable
    );
}