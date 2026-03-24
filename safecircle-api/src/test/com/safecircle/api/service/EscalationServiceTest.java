package com.safecircle.api.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safecircle.api.exception.NotFoundException;
import com.safecircle.api.repository.AlertEventRepository;
import com.safecircle.api.repository.CareCircleRepository;
import com.safecircle.api.service.EscalationService;
import com.safecircle.common.enums.AlertState;
import com.safecircle.common.enums.NotificationChannel;
import com.safecircle.common.enums.TriggerReason;
import com.safecircle.common.model.AlertEvent;
import com.safecircle.common.model.CareCircleMember;
import com.safecircle.common.model.User;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT TEST CONCEPTS:
 *
 * @ExtendWith(MockitoExtension.class) activates Mockito — a mocking framework.
 *
 * MOCK CONCEPT:
 * @Mock creates a fake version of a dependency. When you call a method on a mock,
 * it does nothing by default and returns null/empty. You control its behaviour with:
 *   when(mockRepo.findById(id)).thenReturn(Optional.of(myAlert));
 * This lets you test the service in complete isolation — no database, no Redis,
 * no SQS. The test is fast (milliseconds) and deterministic.
 *
 * @InjectMocks creates the real EscalationService and injects the mocks above into it.
 *
 * WHY test the state machine so thoroughly?
 * The escalation service is the core safety guarantee of the app. A bug here means:
 *  - Contacts get double-notified at 3am
 *  - An alert that should escalate silently stops
 *  - A resolved alert re-escalates
 * These bugs are embarrassing at best, dangerous at worst. Test every transition.
 *
 * @Nested + @DisplayName CONCEPT:
 * @Nested groups related tests visually. @DisplayName gives tests human-readable
 * names that show up in test reports:
 *   "triggerEscalation > when user is inactive > should create alert in NUDGE_SENT state"
 * This makes it obvious what broke when a test fails.
 *
 * GIVEN / WHEN / THEN PATTERN:
 * Each test is structured as:
 *   Given: set up the test data and mock behaviour
 *   When:  call the method under test
 *   Then:  assert the outcome
 * This makes tests readable to non-developers too.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EscalationService")
class EscalationServiceTest {

    @Mock private AlertEventRepository alertEventRepository;
    @Mock private CareCircleRepository careCircleRepository;
    @Mock private SqsTemplate sqsTemplate;

    @InjectMocks private EscalationService escalationService;

    // ObjectMapper is not mocked — we use a real instance since it has no side effects
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
            .id(testUserId)
            .email("test@example.com")
            .fullName("Juan dela Cruz")
            .cognitoSub("cognito-sub-123")
            .phone("+639171234567")
            .build();

        // Inject objectMapper into service (Mockito doesn't inject non-mock fields)
        // In a real project, use @Spy for ObjectMapper or use constructor injection
        org.springframework.test.util.ReflectionTestUtils.setField(
            escalationService, "objectMapper", objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(
            escalationService, "notificationQueueName", "safecircle-notifications");
    }

    @Nested
    @DisplayName("triggerEscalation")
    class TriggerEscalation {

        @Test
        @DisplayName("should create alert in NUDGE_SENT state for INACTIVE reason")
        void shouldCreateAlertInNudgeSentState() {
            // GIVEN
            when(alertEventRepository.save(any(AlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // return what was saved

            // WHEN
            AlertEvent result = escalationService.triggerEscalation(
                testUser, TriggerReason.INACTIVE, 85);

            // THEN
            assertThat(result.getState()).isEqualTo(AlertState.NUDGE_SENT);
            assertThat(result.getTriggerReason()).isEqualTo(TriggerReason.INACTIVE);
            assertThat(result.getMonitoredUser()).isEqualTo(testUser);
            assertThat(result.getBatteryLevelAtTrigger()).isEqualTo(85);
        }

        @Test
        @DisplayName("should enqueue a NUDGE_USER job to SQS")
        void shouldEnqueueNudgeJob() {
            // GIVEN
            when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            escalationService.triggerEscalation(testUser, TriggerReason.INACTIVE, 85);

            // THEN — verify SQS was called exactly once
            verify(sqsTemplate, times(1)).send(any());
        }

        @Test
        @DisplayName("should set null battery level when battery is unknown (-1)")
        void shouldSetNullBatteryWhenUnknown() {
            // GIVEN
            when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            AlertEvent result = escalationService.triggerEscalation(
                testUser, TriggerReason.INACTIVE, -1);

            // THEN
            assertThat(result.getBatteryLevelAtTrigger()).isNull();
        }

        @Test
        @DisplayName("should handle BATTERY_LOW trigger reason")
        void shouldHandleBatteryLowReason() {
            // GIVEN
            when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            AlertEvent result = escalationService.triggerEscalation(
                testUser, TriggerReason.BATTERY_LOW, 5);

            // THEN
            assertThat(result.getTriggerReason()).isEqualTo(TriggerReason.BATTERY_LOW);
            assertThat(result.getBatteryLevelAtTrigger()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("escalateToContacts")
    class EscalateToContacts {

        private UUID alertId;
        private AlertEvent existingAlert;

        @BeforeEach
        void setUpAlert() {
            alertId = UUID.randomUUID();
            existingAlert = AlertEvent.builder()
                .id(alertId)
                .monitoredUser(testUser)
                .triggerReason(TriggerReason.INACTIVE)
                .state(AlertState.NUDGE_SENT)
                .build();
        }

        @Test
        @DisplayName("should transition state to CONTACT_ALERTED")
        void shouldTransitionToContactAlerted() {
            // GIVEN
            when(alertEventRepository.findById(alertId))
                .thenReturn(Optional.of(existingAlert));
            when(careCircleRepository.findActiveByMonitoredUserIdOrderByPriority(testUserId))
                .thenReturn(List.of(buildContact(1, "PUSH", "SMS")));
            when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            escalationService.escalateToContacts(alertId);

            // THEN — capture what was saved and check its state
            ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
            verify(alertEventRepository, atLeastOnce()).save(captor.capture());

            AlertEvent saved = captor.getAllValues().stream()
                .filter(a -> a.getState() == AlertState.CONTACT_ALERTED)
                .findFirst()
                .orElseThrow();
            assertThat(saved.getState()).isEqualTo(AlertState.CONTACT_ALERTED);
        }

        @Test
        @DisplayName("should enqueue one SQS job per channel per contact")
        void shouldEnqueueOneJobPerChannel() {
            // GIVEN — contact has PUSH and SMS channels
            when(alertEventRepository.findById(alertId))
                .thenReturn(Optional.of(existingAlert));
            when(careCircleRepository.findActiveByMonitoredUserIdOrderByPriority(testUserId))
                .thenReturn(List.of(buildContact(1, "PUSH", "SMS")));
            when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            escalationService.escalateToContacts(alertId);

            // THEN — 2 SQS messages: one for PUSH, one for SMS
            verify(sqsTemplate, times(2)).send(any());
        }

        @Test
        @DisplayName("should do nothing if alert is already RESOLVED")
        void shouldSkipIfAlreadyResolved() {
            // GIVEN — alert was already resolved before escalation ran
            existingAlert.setState(AlertState.RESOLVED);
            when(alertEventRepository.findById(alertId))
                .thenReturn(Optional.of(existingAlert));

            // WHEN
            escalationService.escalateToContacts(alertId);

            // THEN — no contacts loaded, no SQS messages sent
            verify(careCircleRepository, never())
                .findActiveByMonitoredUserIdOrderByPriority(any());
            verify(sqsTemplate, never()).send(any());
        }

        @Test
        @DisplayName("should do nothing if alert is already CONTACT_ALERTED")
        void shouldSkipIfAlreadyContactAlerted() {
            // GIVEN
            existingAlert.setState(AlertState.CONTACT_ALERTED);
            when(alertEventRepository.findById(alertId))
                .thenReturn(Optional.of(existingAlert));

            // WHEN
            escalationService.escalateToContacts(alertId);

            // THEN
            verify(careCircleRepository, never())
                .findActiveByMonitoredUserIdOrderByPriority(any());
        }

        @Test
        @DisplayName("should log warning and not fail if care circle is empty")
        void shouldHandleEmptyCareCircle() {
            // GIVEN — user has no contacts yet
            when(alertEventRepository.findById(alertId))
                .thenReturn(Optional.of(existingAlert));
            when(careCircleRepository.findActiveByMonitoredUserIdOrderByPriority(testUserId))
                .thenReturn(Collections.emptyList());

            // WHEN — should NOT throw, just log a warning
            assertThatNoException().isThrownBy(
                () -> escalationService.escalateToContacts(alertId)
            );

            // THEN — no SQS messages sent
            verify(sqsTemplate, never()).send(any());
        }

        @Test
        @DisplayName("should throw NotFoundException for unknown alert ID")
        void shouldThrowForUnknownAlertId() {
            // GIVEN
            UUID unknownId = UUID.randomUUID();
            when(alertEventRepository.findById(unknownId)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> escalationService.escalateToContacts(unknownId))
                .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("should create escalation records for audit trail")
        void shouldCreateEscalationRecords() {
            // GIVEN
            when(alertEventRepository.findById(alertId))
                .thenReturn(Optional.of(existingAlert));
            when(careCircleRepository.findActiveByMonitoredUserIdOrderByPriority(testUserId))
                .thenReturn(List.of(buildContact(1, "PUSH")));
            when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            escalationService.escalateToContacts(alertId);

            // THEN — alert should have 1 escalation record
            ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
            verify(alertEventRepository, atLeastOnce()).save(captor.capture());

            AlertEvent saved = captor.getAllValues().getLast();
            assertThat(saved.getEscalations()).hasSize(1);
            assertThat(saved.getEscalations().get(0).getChannel())
                .isEqualTo(NotificationChannel.PUSH);
        }
    }

    // ——— Helper methods ———

    private CareCircleMember buildContact(int priority, String... channels) {
        User contactUser = User.builder()
            .id(UUID.randomUUID())
            .email("contact@example.com")
            .fullName("Maria dela Cruz")
            .cognitoSub("contact-sub")
            .phone("+639179876543")
            .build();

        return CareCircleMember.builder()
            .id(UUID.randomUUID())
            .monitoredUser(testUser)
            .contactUser(contactUser)
            .priorityOrder(priority)
            .notifyVia(channels)
            .status("ACTIVE")
            .build();
    }
}