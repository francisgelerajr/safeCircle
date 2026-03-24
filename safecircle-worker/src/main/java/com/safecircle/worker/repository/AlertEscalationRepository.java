package com.safecircle.worker.repository;

import com.safecircle.common.model.AlertEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertEscalationRepository extends JpaRepository<AlertEscalation, UUID> {

    /**
     * Finds the escalation record that matches a specific alert + contact + channel.
     * Used after sending a notification to update the status to DELIVERED or FAILED.
     */
    Optional<AlertEscalation> findByAlertEventIdAndContactUserIdAndChannel(
        UUID alertEventId,
        UUID contactUserId,
        String channel
    );

    /**
     * Bulk status update — more efficient than load-then-save for simple updates.
     *
     * @Modifying CONCEPT:
     * @Modifying tells Spring Data this query modifies data (UPDATE/DELETE).
     * Without it, Spring Data treats all @Query methods as reads.
     * clearAutomatically = true flushes the first-level cache after the update —
     * ensures subsequent reads see the updated values.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE AlertEscalation ae
        SET ae.status = :status, ae.ackedAt = :ackedAt
        WHERE ae.id = :id
        """)
    void updateStatus(
        @Param("id") UUID id,
        @Param("status") String status,
        @Param("ackedAt") Instant ackedAt
    );
}