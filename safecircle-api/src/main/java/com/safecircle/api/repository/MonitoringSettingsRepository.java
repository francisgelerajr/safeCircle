package com.safecircle.api.repository;

import com.safecircle.common.model.MonitoringSettings;
import com.safecircle.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitoringSettingsRepository extends JpaRepository<MonitoringSettings, UUID> {

    Optional<MonitoringSettings> findByUser(User user);

    Optional<MonitoringSettings> findByUserId(UUID userId);

    /**
     * Fetches all settings rows where monitoring is active (not paused, or pause has expired).
     * Called by the InactivityCheckerJob every 5 minutes.
     *
     * @Query CONCEPT:
     * When the query is too complex for method-name derivation, use @Query with JPQL.
     * JPQL looks like SQL but uses class names (MonitoringSettings) and field names
     * (isPaused, pausedUntil) instead of table/column names.
     *
     * The colon-prefix :now is a named parameter — @Param("now") binds it to the
     * method argument. This is safe from SQL injection because Spring uses
     * prepared statements under the hood.
     */
    @Query("""
        SELECT ms FROM MonitoringSettings ms
        WHERE ms.isPaused = false
           OR (ms.isPaused = true AND ms.pausedUntil IS NOT NULL AND ms.pausedUntil < :now)
        """)
    List<MonitoringSettings> findAllActiveSettings(@Param("now") Instant now);
}