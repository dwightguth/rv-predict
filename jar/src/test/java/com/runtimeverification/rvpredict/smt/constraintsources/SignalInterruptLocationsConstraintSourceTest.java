package com.runtimeverification.rvpredict.smt.constraintsources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.runtimeverification.rvpredict.log.EventType;
import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;
import com.runtimeverification.rvpredict.smt.ConstraintSource;
import com.runtimeverification.rvpredict.smt.ConstraintType;
import com.runtimeverification.rvpredict.smt.ModelConstraint;
import com.runtimeverification.rvpredict.smt.constraints.EventCanOccurBetweenGoodEventAndBadEvent;
import com.runtimeverification.rvpredict.testutils.ModelConstraintUtils;
import com.runtimeverification.rvpredict.trace.ThreadType;
import com.runtimeverification.rvpredict.util.Constants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SignalInterruptLocationsConstraintSourceTest {
    private static final long SIGNAL_NUMBER_1 = 5;
    private static final long SIGNAL_NUMBER_2 = 6;
    private static final long SIGNAL_NUMBER_3 = 7;
    private static final long MASK_WITH_SIGNAL_1 = (1 << SIGNAL_NUMBER_1);
    private static final long MASK_WITH_SIGNAL_2 = (1 << SIGNAL_NUMBER_2);
    private static final int TTID_1 = 100;
    private static final int TTID_2 = 101;
    private static final int SIGNAL_TTID_3 = 102;
    private static final int SIGNAL_TTID_4 = 103;
    private static final int SIGNAL_TTID_5 = 104;
    private static final long SIGNAL_HANDLER_1 = 200;
    private static final long SIGNAL_HANDLER_2 = 201;
    private static final long SIGNAL_HANDLER_3 = 202;
    private static final Collection<ConstraintType> ALL_CONSTRAINT_TYPES =
            Arrays.asList(ConstraintType.SOUND, ConstraintType.UNSOUND_BUT_FAST);

    @Mock private Function<Integer, ThreadType> mockTtidToThreadType;
    @Mock private Function<Integer, Long> mockTtidToSignalNumber;
    @Mock private Function<Integer, Long> mockTtidToSignalHandler;
    @Mock private Function<Integer, Optional<ReadonlyEventInterface>> mockTtidToStartEvent;
    @Mock private Function<Integer, Optional<ReadonlyEventInterface>> mockTtidToJoinEvent;
    @Mock private Function<Long, Set<Integer>> mockSignalNumberToTtidsWhereEnabledAtStart;
    @Mock private Function<Long, Set<Integer>> mockSignalNumberToTtidsWhereDisabledAtStart;
    @Mock private EventCanOccurBetweenGoodEventAndBadEvent.HappensBefore mockHappensBefore;

    @Mock private ReadonlyEventInterface mockEvent1;
    @Mock private ReadonlyEventInterface mockEvent2;
    @Mock private ReadonlyEventInterface mockEvent3;
    @Mock private ReadonlyEventInterface mockEvent4;
    @Mock private ReadonlyEventInterface mockEnableEvent5;
    @Mock private ReadonlyEventInterface mockEvent6;
    @Mock private ReadonlyEventInterface mockDisableEvent7;
    @Mock private ReadonlyEventInterface mockEvent8;
    @Mock private ReadonlyEventInterface mockEnableEvent9;
    @Mock private ReadonlyEventInterface mockEvent10;
    @Mock private ReadonlyEventInterface mockLockEvent11;
    @Mock private ReadonlyEventInterface mockUnlockEvent12;
    @Mock private ReadonlyEventInterface mockJoinEvent17;
    @Mock private ReadonlyEventInterface mockEvent18;

    @Before
    public void setUp() {
        when(mockTtidToThreadType.apply(TTID_1)).thenReturn(ThreadType.THREAD);
        when(mockTtidToThreadType.apply(TTID_2)).thenReturn(ThreadType.THREAD);
        when(mockTtidToThreadType.apply(SIGNAL_TTID_3)).thenReturn(ThreadType.SIGNAL);
        when(mockTtidToThreadType.apply(SIGNAL_TTID_4)).thenReturn(ThreadType.SIGNAL);
        when(mockTtidToThreadType.apply(SIGNAL_TTID_5)).thenReturn(ThreadType.SIGNAL);

        when(mockTtidToSignalNumber.apply(any())).thenReturn(Constants.INVALID_SIGNAL);
        when(mockTtidToSignalHandler.apply(any())).thenReturn(Constants.INVALID_ADDRESS);
        when(mockTtidToStartEvent.apply(any())).thenReturn(Optional.empty());
        when(mockTtidToJoinEvent.apply(any())).thenReturn(Optional.empty());
        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(any())).thenReturn(Collections.emptySet());
        when(mockSignalNumberToTtidsWhereDisabledAtStart.apply(any())).thenReturn(Collections.emptySet());
        when(mockHappensBefore.check(any(), any())).thenReturn(false);

        when(mockEvent1.getEventId()).thenReturn(1L);
        when(mockEvent2.getEventId()).thenReturn(2L);
        when(mockEvent3.getEventId()).thenReturn(3L);
        when(mockEvent4.getEventId()).thenReturn(4L);
        when(mockEnableEvent5.getEventId()).thenReturn(5L);
        when(mockEvent6.getEventId()).thenReturn(6L);
        when(mockDisableEvent7.getEventId()).thenReturn(7L);
        when(mockEvent8.getEventId()).thenReturn(8L);
        when(mockEnableEvent9.getEventId()).thenReturn(9L);
        when(mockEvent10.getEventId()).thenReturn(10L);
        when(mockLockEvent11.getEventId()).thenReturn(11L);
        when(mockUnlockEvent12.getEventId()).thenReturn(12L);
        when(mockJoinEvent17.getEventId()).thenReturn(17L);
        when(mockEvent18.getEventId()).thenReturn(18L);

        makeWriteMock(mockEvent1);
        makeWriteMock(mockEvent2);
        makeWriteMock(mockEvent3);
        makeWriteMock(mockEvent4);
        when(mockEnableEvent5.getType()).thenReturn(EventType.UNBLOCK_SIGNALS);
        makeWriteMock(mockEvent6);
        when(mockDisableEvent7.getType()).thenReturn(EventType.BLOCK_SIGNALS);
        makeWriteMock(mockEvent8);
        when(mockEnableEvent9.getType()).thenReturn(EventType.UNBLOCK_SIGNALS);
        makeWriteMock(mockEvent10);
        when(mockLockEvent11.getType()).thenReturn(EventType.WRITE_LOCK);
        when(mockUnlockEvent12.getType()).thenReturn(EventType.WRITE_UNLOCK);
        when(mockJoinEvent17.getType()).thenReturn(EventType.JOIN_THREAD);
        makeWriteMock(mockEvent18);

        when(mockLockEvent11.isLock()).thenReturn(true);
        when(mockUnlockEvent12.isUnlock()).thenReturn(true);
    }

    @Test
    public void alwaysTrueWithoutEvents() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of();
        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,
        runTestForConstraintTypes(
                constraintSource, ALL_CONSTRAINT_TYPES,
                constraint -> Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource())));
    }

    @Test
    public void alwaysTrueWithoutSignals() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent1, mockEvent2),
                TTID_2, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,
        runTestForConstraintTypes(
                constraintSource, ALL_CONSTRAINT_TYPES,
                constraint -> Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource())));
    }

    @Test
    public void signalMustInterruptAfterEnable() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent1, mockEnableEvent5, mockEvent6),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,
        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "20", "o3", "30", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "20", "o3", "20", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "20", "o3", "10", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalMustInterruptExistingThread() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent1, mockEnableEvent5, mockEvent6),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,
        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "20", "o3", "30", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "20", "o3", "30", "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalIsDisabledByDisable() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(
                        mockEvent1, mockEnableEvent5, mockEvent6,
                        mockDisableEvent7, mockEvent8, mockEnableEvent9),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);
        when(mockDisableEvent7.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);
        when(mockEnableEvent9.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,
        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "10", "o4", "20", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "30", "o4", "50", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "40", "o4", "50", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "40", "o4", "60", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "40", "o4", "60", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "70", "o4", "80", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "90", "o4", "100", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "30", "o3", "100", "o4", "110", "o7", "60", "o9", "90",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalCanInterruptAThreadWithoutEnableWhenAllowed() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent2, mockEvent6),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));
        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o2", "1", "o3", "2", "o4", "3", "o6", "4",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o2", "1", "o3", "2", "o4", "3", "o6", "4",
                    "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv102", "1")));
        });

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of());
        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            constraint = constraintSource.createConstraint(ConstraintType.SOUND);
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "citv102", Integer.toString(TTID_1))));
        });
    }

    @Test
    public void signalInterruptsAfterJoinWhenEnabledAtStart() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent2, mockEvent6),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToStartEvent.apply(TTID_1)).thenReturn(Optional.of(mockEvent1));
        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "1", "o3", "2", "o4", "3", "o2", "4", "o6", "5",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o3", "1", "o4", "2", "o1", "3", "o2", "4", "o6", "5",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalInterruptsUntilTheFirstDisableWhenEnabledAtStart() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent2, mockDisableEvent7),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockDisableEvent7.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o4", "10", "o7", "20", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o4", "20", "o7", "10", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalInterruptsUntilJoinIfNoDisableWhenEnabledAtStart() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent2),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToJoinEvent.apply(TTID_1)).thenReturn(Optional.of(mockEvent6));
        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o4", "10", "o6", "20", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o4", "20", "o6", "10", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalDoesNotCrossLocksWhenEnabledAtStart() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockLockEvent11, mockUnlockEvent12),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o3", "10", "o4", "20", "o11", "30", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o3", "10", "o4", "50", "o11", "30", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o3", "40", "o4", "50", "o11", "30", "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalDoesNotCrossLocksAfterEnableEvent() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEnableEvent5, mockLockEvent11, mockUnlockEvent12),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "10", "o11", "40", "o12", "70",
                    "o3", "20", "o4", "30",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "10", "o11", "40", "o12", "70",
                    "o3", "20", "o4", "60",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "10", "o11", "40", "o12", "70",
                    "o3", "50", "o4", "60",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalInterruptsSignalWhenAllowedByMask() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockEvent2))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4))
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "1", "o2", "100",
                    "o3", "10", "o4", "40", "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "0")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "1", "o2", "100",
                    "o3", "10", "o4", "40", "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "1", "o2", "100",
                    "o3", "10", "o4", "40", "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "0")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "1", "o2", "100",
                    "o3", "10", "o4", "40", "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "1")));
        });
    }

    @Test
    public void signalDoesNotCrossLocksWhenInterruptingSignalAtStart() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockEvent2))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockLockEvent11, mockUnlockEvent12, mockEvent18))
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o11", "60", "o12", "90", "o18", "100",
                    "o6", "40", "o8", "50",
                    "o13", "5",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "0")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o11", "60", "o12", "90", "o18", "100",
                    "o6", "40", "o8", "80",
                    "o13", "5",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "0")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o11", "60", "o12", "90", "o18", "100",
                    "o6", "70", "o8", "80",
                    "o13", "5",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "0")));
        });
    }

    @Test
    public void signalDoesNotCrossLocksWhenInterruptingSignalAfterEnableEvent() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockEvent2))
                        .put(SIGNAL_TTID_3,
                                ImmutableList.of(mockEvent3, mockEnableEvent5, mockLockEvent11, mockUnlockEvent12))
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_2);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o11", "70", "o12", "100",
                    "o6", "50", "o8", "60",
                    "o13", "5",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o11", "70", "o12", "100",
                    "o6", "50", "o8", "90",
                    "o13", "5",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o11", "70", "o12", "100",
                    "o6", "80", "o8", "90",
                    "o13", "5",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
        });
    }

    @Test
    public void signalsCanEnableOtherSignals() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockEvent2))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEnableEvent5, mockEvent10))
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_2);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "50", "o8", "60",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "0")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "33", "o8", "60",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "0")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "33", "o8", "36",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "0")));
        });
    }

    @Test
    public void signalsInterruptThreadsOnlyBeforeTheirJoinEvent() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockJoinEvent17, mockEvent18))
                        .put(TTID_2, ImmutableList.of(mockEnableEvent5, mockEvent8))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent6))
                        .build();

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        when(mockTtidToJoinEvent.apply(TTID_2)).thenReturn(Optional.of(mockJoinEvent17));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o17", "60", "o18", "70",
                    "o5", "20", "o8", "30",
                    "o3", "40", "o6", "50",
                    "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv101", "1", "cidv102", "2")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o17", "60", "o18", "90",
                    "o5", "20", "o8", "30",
                    "o3", "40", "o6", "80",
                    "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv101", "1", "cidv102", "2")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o17", "60", "o18", "80",
                    "o5", "20", "o8", "30",
                    "o3", "70", "o6", "80",
                    "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv101", "1", "cidv102", "2")));
        });
    }

    @Test
    public void signalsInterruptSignalsOnlyBeforeTheirLastEvent() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockEvent2))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEnableEvent5, mockEvent10))
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_2);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "50", "o8", "60",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "50", "o8", "90",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "80", "o8", "90",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
        });
    }

    @Test
    public void interruptedTtidIsSetWhenSignalsInterruptThreads() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEnableEvent5, mockEvent8))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent6))
                        .build();

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_1);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "10", "o8", "20",
                    "o3", "30", "o6", "40",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o5", "10", "o8", "20",
                    "o3", "30", "o6", "40",
                    "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void interruptedTtidIsSetWhenSignalsInterruptSignals() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, ImmutableList.of(mockEvent1, mockEvent2))
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEnableEvent5, mockEvent10))
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockEnableEvent5.getPartialSignalMask()).thenReturn(MASK_WITH_SIGNAL_2);

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "50", "o8", "60",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o1", "10", "o2", "20",
                    "o3", "30", "o5", "40", "o10", "70",
                    "o6", "50", "o8", "60",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2")));
        });
    }

    @Test
    public void soundIterationDoesNotCrossMultipleWrites() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId = ImmutableMap.of(
                TTID_1, ImmutableList.of(mockEvent2, mockEvent6, mockEvent8),
                SIGNAL_TTID_3, ImmutableList.of(mockEvent3, mockEvent4));
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(
                    ModelConstraintUtils.mockVariableSource(
                            "o2", "10", "o3", "20", "o4", "30", "o6", "40", "o8", "50",
                            "citv102", Integer.toString(TTID_1),
                            "cidv100", "0", "cidv102", "1")));
            Assert.assertTrue(constraint.evaluate(
                    ModelConstraintUtils.mockVariableSource(
                            "o2", "10", "o3", "20", "o6", "30", "o4", "40", "o8", "50",
                            "citv102", Integer.toString(TTID_1),
                            "cidv100", "0", "cidv102", "1")));
        });
        runTestForConstraintTypes(
                constraintSource, Collections.singletonList(ConstraintType.SOUND), constraint ->
                        Assert.assertFalse(constraint.evaluate(
                                ModelConstraintUtils.mockVariableSource(
                                        "o2", "10", "o3", "20", "o6", "30", "o8", "40", "o4", "50",
                                        "citv102", Integer.toString(TTID_1),
                                        "cidv100", "0", "cidv102", "1"))));
        runTestForConstraintTypes(
                constraintSource, Collections.singletonList(ConstraintType.UNSOUND_BUT_FAST), constraint ->
                        Assert.assertTrue(constraint.evaluate(
                                ModelConstraintUtils.mockVariableSource(
                                        "o2", "10", "o3", "20", "o6", "30", "o8", "40", "o4", "50",
                                        "citv102", Integer.toString(TTID_1),
                                        "cidv100", "0", "cidv102", "1"))));
    }

    @Test
    public void signalInterruptsEmptyThread() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, Collections.emptyList())
                        .put(SIGNAL_TTID_3, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1),
                    "cidv100", "0", "cidv102", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_2),
                    "cidv100", "0", "cidv102", "1")));
        });
    }

    @Test
    public void signalInterruptsEmptySignalWhichInterruptsThread() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, Collections.emptyList())
                        .put(SIGNAL_TTID_3, Collections.emptyList())
                        .put(SIGNAL_TTID_4, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "0")));
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "1", "sm_103_6", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "0")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2",
                    "sm_102_6", "0", "sm_103_6", "1")));
        });
    }

    @Test
    public void signalInterruptsEmptySignalWhichInterruptsEmptySignalWhichInterruptsThread() {
        Map<Integer, List<ReadonlyEventInterface>> eventsByThreadId =
                new ImmutableMap.Builder<Integer, List<ReadonlyEventInterface>>()
                        .put(TTID_1, Collections.emptyList())
                        .put(SIGNAL_TTID_3, Collections.emptyList())
                        .put(SIGNAL_TTID_4, Collections.emptyList())
                        .put(SIGNAL_TTID_5, ImmutableList.of(mockEvent6, mockEvent8))
                        .build();

        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_NUMBER_1);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_3)).thenReturn(SIGNAL_HANDLER_1);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_NUMBER_2);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_4)).thenReturn(SIGNAL_HANDLER_2);
        when(mockTtidToSignalNumber.apply(SIGNAL_TTID_5)).thenReturn(SIGNAL_NUMBER_3);
        when(mockTtidToSignalHandler.apply(SIGNAL_TTID_5)).thenReturn(SIGNAL_HANDLER_3);

        when(mockSignalNumberToTtidsWhereEnabledAtStart.apply(SIGNAL_NUMBER_1)).thenReturn(ImmutableSet.of(TTID_1));

        ConstraintSource constraintSource = new SignalInterruptLocationsConstraintSource(
                eventsByThreadId,
                mockTtidToThreadType,
                mockTtidToSignalNumber,
                mockTtidToStartEvent,
                mockTtidToJoinEvent,
                mockSignalNumberToTtidsWhereEnabledAtStart,
                mockSignalNumberToTtidsWhereDisabledAtStart,
                true);  // detectInterruptedThreadRace,

        runTestForConstraintTypes(constraintSource, ALL_CONSTRAINT_TYPES, constraint -> {
            Assert.assertTrue(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "citv104", Integer.toString(SIGNAL_TTID_4),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2", "cidv104", "3",
                    "sm_102_6", "1", "sm_103_7", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "citv104", Integer.toString(SIGNAL_TTID_4),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2", "cidv104", "3",
                    "sm_102_6", "0", "sm_103_7", "1")));
            Assert.assertFalse(constraint.evaluate(ModelConstraintUtils.mockVariableSource(
                    "o6", "20", "o8", "30",
                    "citv102", Integer.toString(TTID_1), "citv103", Integer.toString(SIGNAL_TTID_3),
                    "citv104", Integer.toString(SIGNAL_TTID_4),
                    "cidv100", "0", "cidv102", "1", "cidv103", "2", "cidv104", "3",
                    "sm_102_6", "1", "sm_103_7", "0")));
        });
    }

    private void runTestForConstraintTypes(
            ConstraintSource constraintSource,
            Collection<ConstraintType> constraintTypes,
            Consumer<ModelConstraint> asserter) {
        for (ConstraintType constraintType : constraintTypes) {
            ModelConstraint constraint = constraintSource.createConstraint(constraintType);
            asserter.accept(constraint);
        }
    }

    private void makeWriteMock(ReadonlyEventInterface mockEvent) {
        when(mockEvent.getType()).thenReturn(EventType.WRITE);
        when(mockEvent.isReadOrWrite()).thenReturn(true);
        when(mockEvent.isWrite()).thenReturn(true);
    }
}
