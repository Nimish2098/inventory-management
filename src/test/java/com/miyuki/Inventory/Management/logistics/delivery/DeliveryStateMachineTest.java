package com.miyuki.Inventory.Management.logistics.delivery;

import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryAction.*;
import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The expected table below is written out independently of the implementation on purpose: if someone edits the
 * state machine, this test must be edited too, which forces a conscious decision.
 */
class DeliveryStateMachineTest {

    record Edge(DeliveryStatus from, DeliveryAction action, DeliveryStatus to) {}

    static final List<Edge> VALID = List.of(
            new Edge(CREATED, ASSIGN, ASSIGNED),
            new Edge(CREATED, CANCEL, CANCELLED),
            new Edge(ASSIGNED, UNASSIGN, CREATED),
            new Edge(ASSIGNED, PICK_UP, PICKED_UP),
            new Edge(ASSIGNED, CANCEL, CANCELLED),
            new Edge(PICKED_UP, START_TRANSIT, IN_TRANSIT),
            new Edge(PICKED_UP, FAIL, FAILED),
            new Edge(IN_TRANSIT, ARRIVE, ARRIVED),
            new Edge(IN_TRANSIT, FAIL, FAILED),
            new Edge(ARRIVED, DELIVER, DELIVERED),
            new Edge(ARRIVED, FAIL, FAILED),
            new Edge(FAILED, RETRY, IN_TRANSIT),
            new Edge(FAILED, RETURN, RETURNED));

    static Stream<Arguments> validTransitions() {
        return VALID.stream().map(e -> Arguments.of(e.from(), e.action(), e.to()));
    }

    static Stream<Arguments> invalidTransitions() {
        Set<String> valid = VALID.stream().map(e -> e.from() + ":" + e.action()).collect(Collectors.toSet());
        return Arrays.stream(DeliveryStatus.values())
                .flatMap(status -> Arrays.stream(DeliveryAction.values())
                        .filter(action -> !valid.contains(status + ":" + action))
                        .map(action -> Arguments.of(status, action)));
    }

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @MethodSource("validTransitions")
    void validTransitionIsAllowed(DeliveryStatus from, DeliveryAction action, DeliveryStatus to) {
        assertThat(DeliveryStateMachine.next(from, action)).contains(to);
        assertThat(DeliveryStateMachine.requireNext(from, action)).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} cannot {1}")
    @MethodSource("invalidTransitions")
    void everyOtherCombinationIsRejected(DeliveryStatus from, DeliveryAction action) {
        assertThat(DeliveryStateMachine.next(from, action)).isEmpty();
        assertThatThrownBy(() -> DeliveryStateMachine.requireNext(from, action))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining(from.name());
    }

    @ParameterizedTest
    @EnumSource(
            value = DeliveryStatus.class,
            names = {"DELIVERED", "RETURNED", "CANCELLED"})
    void terminalStatesAllowNothing(DeliveryStatus status) {
        assertThat(DeliveryStateMachine.isTerminal(status)).isTrue();
        assertThat(DeliveryStateMachine.allowedActions(status)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = DeliveryStatus.class,
            names = {"DELIVERED", "RETURNED", "CANCELLED"},
            mode = EnumSource.Mode.EXCLUDE)
    void nonTerminalStatesHaveAtLeastOneExit(DeliveryStatus status) {
        assertThat(DeliveryStateMachine.isTerminal(status)).isFalse();
        assertThat(DeliveryStateMachine.allowedActions(status)).isNotEmpty();
    }

    @Test
    void allowedActionsMatchTheTable() {
        assertThat(DeliveryStateMachine.allowedActions(CREATED)).containsExactly(ASSIGN, CANCEL);
        assertThat(DeliveryStateMachine.allowedActions(ASSIGNED)).containsExactly(UNASSIGN, PICK_UP, CANCEL);
        assertThat(DeliveryStateMachine.allowedActions(FAILED)).containsExactly(RETRY, RETURN);
    }

    @Test
    void everyStatusIsReachableFromCreated() {
        Set<DeliveryStatus> seen = EnumSet.of(CREATED);
        ArrayDeque<DeliveryStatus> queue = new ArrayDeque<>(List.of(CREATED));
        while (!queue.isEmpty()) {
            DeliveryStatus current = queue.poll();
            for (DeliveryAction action : DeliveryAction.values()) {
                DeliveryStateMachine.next(current, action).ifPresent(next -> {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                });
            }
        }
        assertThat(seen).containsExactlyInAnyOrder(DeliveryStatus.values());
    }

    @Test
    void everyActionIsUsedByAtLeastOneTransition() {
        Set<DeliveryAction> used = VALID.stream().map(Edge::action).collect(Collectors.toSet());
        assertThat(used).containsExactlyInAnyOrder(DeliveryAction.values());
    }

    @Test
    void allowedActionsCannotBeModifiedByCallers() {
        Set<DeliveryAction> actions = DeliveryStateMachine.allowedActions(CREATED);
        assertThatThrownBy(() -> actions.add(DELIVER)).isInstanceOf(UnsupportedOperationException.class);
    }
}
