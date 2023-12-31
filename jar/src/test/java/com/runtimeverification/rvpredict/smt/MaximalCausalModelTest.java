package com.runtimeverification.rvpredict.smt;

import com.runtimeverification.rvpredict.config.Configuration;
import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;
import com.runtimeverification.rvpredict.log.compact.Context;
import com.runtimeverification.rvpredict.log.compact.InvalidTraceDataException;
import com.runtimeverification.rvpredict.metadata.Metadata;
import com.runtimeverification.rvpredict.performance.AnalysisLimit;
import com.runtimeverification.rvpredict.testutils.TraceUtils;
import com.runtimeverification.rvpredict.trace.RawTrace;
import com.runtimeverification.rvpredict.trace.ThreadInfos;
import com.runtimeverification.rvpredict.trace.Trace;
import com.runtimeverification.rvpredict.trace.TraceState;
import com.runtimeverification.rvpredict.util.Logger;
import com.runtimeverification.rvpredict.violation.Race;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.runtimeverification.rvpredict.testutils.TraceUtils.extractSingleEvent;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MaximalCausalModelTest {
    private static final int WINDOW_SIZE = 100;
    private static final int TIMEOUT_SECONDS = 2;
    private static final int UNLIMITED_SIGNAL_DEPTH = 0;
    private static final int ONE_SIGNAL_DEPTH = 1;
    private static final int TWO_SIGNAL_DEPTH = 2;
    private static final long ADDRESS_1 = 200;
    private static final long ADDRESS_2 = 201;
    private static final long ADDRESS_3 = 202;
    private static final long VALUE_1 = 300;
    private static final long VALUE_2 = 301;
    private static final long BASE_ID = 0;
    private static final long BASE_PC = 400;
    private static final long THREAD_1 = 1;
    private static final long THREAD_2 = 2;
    private static final long THREAD_3 = 3;
    private static final long THREAD_4 = 4;
    private static final int NO_SIGNAL = 0;
    private static final int ONE_SIGNAL = 1;
    private static final int TWO_SIGNALS = 2;
    private static final long LOCK_1 = 500;
    private static final long LOCK_2 = 501;
    private static final long SIGNAL_NUMBER_1 = 1;
    private static final long SIGNAL_NUMBER_2 = 2;
    private static final long SIGNAL_NUMBER_63 = 63;
    private static final long SIGNAL_HANDLER_1 = 600;
    private static final long SIGNAL_HANDLER_2 = 601;
    private static final long ALL_SIGNALS_DISABLED_MASK = 0xffffffffffffffffL;
    private static final long SIGNAL_1_ENABLED_MASK = ~(1L << SIGNAL_NUMBER_1);
    private static final long SIGNAL_2_ENABLED_MASK = ~(1L << SIGNAL_NUMBER_2);
    private static final long SIGNALS_1_AND_2_ENABLED_MASK = SIGNAL_1_ENABLED_MASK & SIGNAL_2_ENABLED_MASK;
    private static final long SIGNAL_63_ENABLED_MASK = 0x7fffffffffffffffL;
    private static final long GENERATION_1 = 700;

    private int nextIdDelta = 0;

    @Mock private Configuration mockConfiguration;
    @Mock private Context mockContext;
    @Mock private Metadata mockMetadata;

    @Before
    public void setUp() {
        nextIdDelta = 0;
        when(mockContext.newId()).then(invocation -> BASE_ID + nextIdDelta++);
        when(mockContext.createUniqueSignalHandlerId(SIGNAL_NUMBER_1)).thenReturn(1L);
        when(mockContext.createUniqueDataAddressId(ADDRESS_1)).thenReturn(2L);
        when(mockContext.createUniqueDataAddressId(ADDRESS_2)).thenReturn(3L);
        when(mockContext.createUniqueDataAddressId(ADDRESS_3)).thenReturn(4L);
        mockConfiguration.solver_timeout = TIMEOUT_SECONDS;
    }

    @Test
    public void detectsSimpleRace() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotDetectRaceBeforeThreadStart() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.threadStart(THREAD_2),
                        tu.threadJoin(THREAD_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }
    @Test
    public void doesNotDetectRaceAfterThreadJoin() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.threadStart(THREAD_2),
                        tu.threadJoin(THREAD_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void detectsRaceBetweenThreadStartAndJoin() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.threadStart(THREAD_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.threadJoin(THREAD_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotDetectRaceWhenLocked() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.lock(LOCK_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void detectsRaceWhenOnlyOneThreadIsLocked() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_1)));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void noRaceBecauseOfConstraintsOnDifferentThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;
        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_3, NO_SIGNAL),
                e1 = tu.nonAtomicStore(ADDRESS_3, VALUE_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_1, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_1, VALUE_2),
                tu.unlock(LOCK_1),
                e2 = tu.nonAtomicStore(ADDRESS_3, VALUE_1)
        );

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_3, NO_SIGNAL));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void detectsRaceWithSignalOnDifferentThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.setSignalHandler(
                                SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void detectsRaceWithSignalOnSameThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(
                                SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRace(
                rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }

    @Test
    public void detectsRaceWithEmptyThreadInterruption() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(
                                SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotDetectRaceBeforeEnablingSignals() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(
                                SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.threadStart(THREAD_2),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotDetectRaceWithDifferentHandlerAddress() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                // Disable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.setSignalHandler(
                        SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                // Enable the signal
                tu.enableSignal(SIGNAL_NUMBER_1),

                // Run the signal
                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),

                // Disable the signal and set a different signal handler, then enable it again.
                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                tu.enableSignal(SIGNAL_NUMBER_1),
                // Enable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL));


        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void detectsRaceWithTheSameHandlerAddress() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                // Disable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                // Enable the signal
                tu.enableSignal(SIGNAL_NUMBER_1),

                // Run the signal
                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),

                // Disable the signal and set a different signal handler, then enable it again.
                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                tu.enableSignal(SIGNAL_NUMBER_1),
                // Enable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                // Disable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                // Disable the signal and set the racy signal handler, then enable it again.
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                tu.enableSignal(SIGNAL_NUMBER_1),
                // Enable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                // Disable the racy instruction on thread 2.
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL));


        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotDetectRaceWhenDisabledAndInterruptsThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                tu.enableSignal(SIGNAL_NUMBER_1),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL));


        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void detectsRaceBetweenEnableAndDisable() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.enableSignal(SIGNAL_NUMBER_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void movesSignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.enableSignal(SIGNAL_NUMBER_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotDetectRaceBeforeEnabling() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.setSignalHandler(
                        SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.enableSignal(SIGNAL_NUMBER_1),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.lock(LOCK_1),
                tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                tu.unlock(LOCK_1),
                tu.lock(LOCK_1),
                tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                tu.unlock(LOCK_1),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotGenerateRacesByInterruptingThreadBeforeBeingStarted() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void doesNotGenerateRacesByInterruptingThreadAfterBeingJoined() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_2),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.threadJoin(THREAD_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void signalCanGenerateRacesEvenIfFullyDisabledOnAnotherThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_3),
                        tu.threadJoin(THREAD_3),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.threadJoin(THREAD_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_3, NO_SIGNAL),
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void signalsDoNotGenerateRacesAfterDisestablishingThem() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.setSignalHandler(
                                SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.nonAtomicStore(ADDRESS_3, VALUE_2),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_2),
                        tu.lock(LOCK_1),
                        tu.nonAtomicLoad(ADDRESS_3, VALUE_1),
                        tu.unlock(LOCK_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.disestablishSignal(SIGNAL_NUMBER_1),
                        tu.lock(LOCK_1),
                        tu.nonAtomicStore(ADDRESS_3, VALUE_1),
                        tu.unlock(LOCK_1),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu));
    }

    @Test
    public void getSignalMaskMayShowThatASignalIsEnabled() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.getSignalMask(SIGNAL_1_ENABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(
                hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }

    @Test
    public void enableSignalStopsFromDetectingRacesBeforeEnabling() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(
                hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }

    @Test
    public void getSetSignalMaskMayShowThatASignalIsEnabledBefore() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.getSetSignalMask(SIGNAL_1_ENABLED_MASK, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(
                hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }

    @Test
    public void setSignalMaskEnablesSignals() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(SIGNAL_1_ENABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(
                hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }

    @Test
    public void setSignalMaskEnablesSignalsLongMask() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(SIGNAL_63_ENABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_63)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_63, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(
                hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }

    public void recurrentSignalFix() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(SIGNAL_1_ENABLED_MASK),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(
                hasRace(rawTraces, extractSingleEvent(e1), extractSingleEvent(e2), tu, true));
    }
/*
    @Test
    public void detectedSignalRaceContainsInterruptedEventWhenOnSameThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Optional<Race> maybeRace =
                findRace(rawTraces, event1, event2, tu);
        Assert.assertTrue(maybeRace.isPresent());
        Race race = maybeRace.get();
        List<Race.SignalStackEvent> signalEvents = race.getFirstSignalStack();
        Assert.assertEquals(1, signalEvents.size());
        Race.SignalStackEvent stackEvent = signalEvents.get(0);
        Optional<ReadonlyEventInterface> maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event1.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(1, stackEvent.getTtid());
        signalEvents = race.getSecondSignalStack();
        Assert.assertEquals(2, signalEvents.size());
        stackEvent = signalEvents.get(0);
        maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event2.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(2, stackEvent.getTtid());
        stackEvent = signalEvents.get(1);
        maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event1.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(1, stackEvent.getTtid());
    }

    @Test
    public void detectedSignalRaceContainsInterruptedEventWhenOnDifferentThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.lock(LOCK_1),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_2),
                        tu.unlock(LOCK_1),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_2),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Optional<Race> maybeRace =
                findRace(rawTraces, event1, event2, tu);
        Assert.assertTrue(maybeRace.isPresent());
        Race race = maybeRace.get();
        List<Race.SignalStackEvent> signalEvents = race.getFirstSignalStack();
        Assert.assertEquals(1, signalEvents.size());
        Race.SignalStackEvent stackEvent = signalEvents.get(0);
        Optional<ReadonlyEventInterface> maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event1.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(1, stackEvent.getTtid());

        signalEvents = race.getSecondSignalStack();
        Assert.assertEquals(2, signalEvents.size());
        stackEvent = signalEvents.get(0);
        maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event2.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(3, stackEvent.getTtid());
        stackEvent = signalEvents.get(1);
        Assert.assertEquals(2, stackEvent.getTtid());
    }

    @Test
    public void stackTraceBeforeTheFirstEventOfAThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.threadStart(THREAD_2),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.disableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Optional<Race> maybeRace =
                findRace(rawTraces, event1, event2, tu);
        Assert.assertTrue(maybeRace.isPresent());
        Race race = maybeRace.get();
        List<Race.SignalStackEvent> signalEvents = race.getFirstSignalStack();
        Assert.assertEquals(1, signalEvents.size());
        Race.SignalStackEvent stackEvent = signalEvents.get(0);
        Optional<ReadonlyEventInterface> maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event1.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(1, stackEvent.getTtid());

        signalEvents = race.getSecondSignalStack();
        Assert.assertEquals(2, signalEvents.size());
        stackEvent = signalEvents.get(0);
        maybeEvent = stackEvent.getEvent();
        Assert.assertTrue(maybeEvent.isPresent());
        Assert.assertEquals(event2.getEventId(), maybeEvent.get().getEventId());
        Assert.assertEquals(3, stackEvent.getTtid());
        stackEvent = signalEvents.get(1);
        Assert.assertFalse(stackEvent.getEvent().isPresent());
        Assert.assertEquals(2, stackEvent.getTtid());
    }
*/
    @Test
    public void eventIdsDoNotCollide() throws InvalidTraceDataException {
        when(mockContext.newId()).thenReturn(1L).thenReturn(2L).thenReturn(3L).thenReturn(4L)
                .thenReturn(1L + WINDOW_SIZE).thenReturn(2L + WINDOW_SIZE).thenReturn(3L + WINDOW_SIZE)
                .thenReturn(4L + WINDOW_SIZE);
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.lock(LOCK_1),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_1),
                        e1 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void interruptedThreadRacesWithSignalMovedToInterruptSignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalDoesNotRaceWithSignalMovedToInterruptItWhenDisabled() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> previousSigest =
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.disableSignal(SIGNAL_NUMBER_2),
                tu.enableSignal(SIGNAL_NUMBER_1),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),

                tu.switchThread(THREAD_1, TWO_SIGNALS),
                tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2));
        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, TWO_SIGNALS));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, previousSigest, true));
    }

    @Test
    public void signalRacesWithSignalMovedToInterruptItWhenEnabled() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> previousSigest =
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.disableSignal(SIGNAL_NUMBER_2),
                tu.enableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),

                tu.switchThread(THREAD_1, TWO_SIGNALS),
                tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2));
        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, TWO_SIGNALS));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, previousSigest, true));
    }

    @Test
    public void signalsDisabledByAtomicEvents() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.atomicStore(ADDRESS_1, VALUE_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.atomicLoad(ADDRESS_1, VALUE_1),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_2)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void raceWithSignalThatInterruptsSignalExplicitSigset() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.disableSignal(SIGNAL_NUMBER_2),
                tu.atomicStore(ADDRESS_2, VALUE_2),
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                tu.enableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                tu.atomicStore(ADDRESS_2, VALUE_1),

                tu.switchThread(THREAD_1, TWO_SIGNALS),
                tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                tu.atomicLoad(ADDRESS_2, VALUE_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.atomicStore(ADDRESS_2, VALUE_2),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2));
        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, TWO_SIGNALS));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void noRaceWithSignalThatInterruptsSignalWithDepthLimit() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<ReadonlyEventInterface>> events = Arrays.asList(
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.disableSignal(SIGNAL_NUMBER_2),
                tu.atomicStore(ADDRESS_2, VALUE_2),
                tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                tu.enableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                tu.atomicStore(ADDRESS_2, VALUE_1),

                tu.switchThread(THREAD_2, ONE_SIGNAL),
                tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                tu.atomicLoad(ADDRESS_2, VALUE_1),
                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, ONE_SIGNAL),
                tu.atomicStore(ADDRESS_2, VALUE_2),
                tu.exitSignal(),

                tu.switchThread(THREAD_1, NO_SIGNAL),
                e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                tu.disableSignal(SIGNAL_NUMBER_1),
                tu.enableSignal(SIGNAL_NUMBER_2),

                tu.switchThread(THREAD_2, NO_SIGNAL),
                tu.atomicStore(ADDRESS_2, VALUE_2)
        );
        List<RawTrace> rawTraces = Arrays.asList(
                tu.extractRawTrace(events, THREAD_1, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, NO_SIGNAL),
                tu.extractRawTrace(events, THREAD_1, ONE_SIGNAL),
                tu.extractRawTrace(events, THREAD_2, ONE_SIGNAL));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true, ONE_SIGNAL_DEPTH));
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true, TWO_SIGNAL_DEPTH));
    }

    @Test
    public void signalEndsBeforeInterruptedSignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalInterruptsSignalWhenAllowedByTheHandlerMask() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.atomicStore(ADDRESS_2, VALUE_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.atomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.atomicStore(ADDRESS_2, VALUE_1),
                        tu.atomicStore(ADDRESS_2, VALUE_2),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        tu.atomicLoad(ADDRESS_2, VALUE_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsInterruptingTheSameThreadCannotOverlap() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e1 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsInterruptingDifferentThreadsCanOverlap() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e1 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsThatInterruptBetweenTwoSigsetEventsUseTheMaskFromTheFirst() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.atomicStore(ADDRESS_2, VALUE_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.atomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.atomicStore(ADDRESS_2, VALUE_1),
                        tu.atomicStore(ADDRESS_2, VALUE_2),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        tu.atomicLoad(ADDRESS_2, VALUE_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalDoesNotInterruptSignalWhenImplicitlyDisabled() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.getSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalIsNotEnabledByHandlerMaskWithDifferentHandler() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_2, SIGNAL_2_ENABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_2, GENERATION_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_2),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalDoesNotInterruptSignalWhenDisabledByTheHandlerMask() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_2, GENERATION_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void keepsLockStateBetweenWindows() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces1 = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.lock(LOCK_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1)));

        List<RawTrace> rawTraces2 = Arrays.asList(
                tu.createRawTrace(
                        false,
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.unlock(LOCK_1)),
                tu.createRawTrace(
                        false,
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        e2 = tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.unlock(LOCK_1)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRaceMultipleWindows(
                Arrays.asList(rawTraces1, rawTraces2), event1, event2, tu, true));
    }

    @Test
    public void keepsSignalEstablishEventsBetweenWindows() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces2 = Arrays.asList(
                tu.createRawTrace(
                        true,
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.atomicStore(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.atomicStore(ADDRESS_1, VALUE_2)),
                tu.createRawTrace(
                        true,
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.atomicStore(ADDRESS_1,VALUE_2),
                        tu.atomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        true,
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.atomicLoad(ADDRESS_1, VALUE_2),
                        e2 = tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        List<RawTrace> rawTraces1 = Arrays.asList(
                tu.createRawTrace(
                        false,
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1)));

        Assert.assertTrue(hasRaceMultipleWindows(
                Arrays.asList(rawTraces1, rawTraces2), event1, event2, tu, true));

        rawTraces1 = Arrays.asList(
                tu.createRawTrace(
                        false,
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1)));

        Assert.assertFalse(hasRaceMultipleWindows(
                Arrays.asList(rawTraces1, rawTraces2), event1, event2, tu, true));
    }

    @Test
    public void usesThreadMaskForSignalMask() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),

                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, GENERATION_1),
                        e2 = tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalDoesNotRaceWithThreadBecauseTheyOverlapTooMuchAtomicDisabling() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.atomicStore(ADDRESS_2, VALUE_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalDoesNotRaceWithThreadBecauseTheyOverlapTooMuchNonAtomicDisabling()
            throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.nonAtomicStore(ADDRESS_2, VALUE_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalDoesNotRaceWithThreadBecauseTheyOverlapTooMuchSignalOnSignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.nonAtomicStore(ADDRESS_3, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.atomicStore(ADDRESS_2, VALUE_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.atomicStore(ADDRESS_3, VALUE_2),
                        tu.atomicStore(ADDRESS_3, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.atomicLoad(ADDRESS_3, VALUE_2),
                        e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalCanInterruptEmptyThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTracesPreviousWindow = Collections.singletonList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1)));

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.disableSignal(SIGNAL_NUMBER_1),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2=tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRaceMultipleWindows(
                Arrays.asList(rawTracesPreviousWindow, rawTraces), event1, event2, tu, true));
    }

    @Test
    public void signalCanInterruptEmptySignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTracesPreviousWindow = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1))
        );

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, GENERATION_1),
                        e2=tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, TWO_SIGNALS),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        Assert.assertTrue(hasRaceMultipleWindows(
                Arrays.asList(rawTracesPreviousWindow, rawTraces), event1, event2, tu, true));
    }

    @Test
    public void signalCannotInterruptTheSameThreadAsAnEmptySignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTracesPreviousWindow = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1))
        );

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.disableSignal(SIGNAL_NUMBER_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e2=tu.nonAtomicLoad(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        Assert.assertFalse(hasRaceMultipleWindows(
                Arrays.asList(rawTracesPreviousWindow, rawTraces), event1, event2, tu, true));
    }

    @Test
    public void noRaceForThreadsWithoutSharedVariables() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        e1 = tu.nonAtomicStore(ADDRESS_1, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_2))
        );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);

        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsDoNotInterruptAtomicEvents() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.atomicStore(ADDRESS_1, VALUE_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.atomicLoad(ADDRESS_1, VALUE_2),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsDoNotInterruptEstablishEvents() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_2, SIGNAL_2_ENABLED_MASK),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsInterruptUnfinishedSignals() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, TWO_SIGNALS),
                        tu.enterSignal(SIGNAL_NUMBER_2, SIGNAL_HANDLER_1, GENERATION_1),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1))
                );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }
    // TODO(virgil): Uncomment these when signal mask reads become consistent.
    /*
    @Test
    public void signalMaskReadsAreConsistentWithInterruptedThreadDisabling() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.getSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalMaskReadsAreConsistentWithSigestMaskDisabling() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.getSignalMask(SIGNAL_2_ENABLED_MASK),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalMaskReadsAreConsistentWithEnabledSignals() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.getSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }
*/
    @Test
    public void signalMaskReadsConstraintsCanBeSatisfied() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.enableSignal(SIGNAL_NUMBER_1),
                        tu.enableSignal(SIGNAL_NUMBER_2),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, SIGNAL_2_ENABLED_MASK),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.getSignalMask(SIGNAL_2_ENABLED_MASK),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void incompatibilityBetweenPrefixReads() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_3, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_2),
                        e1 = tu.nonAtomicLoad(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        tu.threadStart(THREAD_2),
                        tu.threadStart(THREAD_3))
                );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void twoDifferentPrefixReadsNeedAWriteBetween() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.atomicLoad(ADDRESS_1, VALUE_1),
                        tu.atomicLoad(ADDRESS_1, VALUE_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.atomicStore(ADDRESS_1, VALUE_2))
        );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void twoDifferentReadsNeedAWriteBetween() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, NO_SIGNAL),
                        tu.atomicStore(ADDRESS_1, VALUE_1),
                        tu.atomicLoad(ADDRESS_1, VALUE_1),
                        tu.atomicLoad(ADDRESS_1, VALUE_2),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.atomicStore(ADDRESS_1, VALUE_2))
        );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void sameWriteOnDifferentThreadMustBeBeforeReadWithSameWriteBefore() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.lock(LOCK_1),
                        tu.lock(LOCK_2),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_2),
                        tu.unlock(LOCK_1),
                        tu.lock(LOCK_1),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.unlock(LOCK_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_2, NO_SIGNAL),
                        tu.lock(LOCK_2),
                        tu.nonAtomicLoad(ADDRESS_1, VALUE_2),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.unlock(LOCK_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_3, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        tu.lock(LOCK_2),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                        tu.unlock(LOCK_2),
                        tu.unlock(LOCK_1)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_4, NO_SIGNAL),
                        tu.lock(LOCK_1),
                        tu.lock(LOCK_2),
                        tu.nonAtomicStore(ADDRESS_1, VALUE_2),
                        tu.unlock(LOCK_2),
                        tu.unlock(LOCK_1)
                )
        );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void doesNotCrashForConsistencyWithMaskJustBeforeSignal() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<RawTrace> rawTraces = Arrays.asList(
                tu.createRawTrace(
                        tu.setSignalMask(ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                        tu.setSignalMask(SIGNAL_1_ENABLED_MASK),
                        e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.enableSignal(SIGNAL_NUMBER_2)),
                tu.createRawTrace(
                        tu.switchThread(THREAD_1, ONE_SIGNAL),
                        tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                        tu.getSignalMask(SIGNALS_1_AND_2_ENABLED_MASK),
                        e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                        tu.exitSignal()));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRace(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void raceWithPreviousSignals() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        when(mockConfiguration.desiredInterruptsPerSignalAndWindow()).thenReturn(1);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<RawTrace>> rawTraces = Arrays.asList(
                Arrays.asList(
                    tu.createRawTrace(
                            tu.setSignalMask(ALL_SIGNALS_DISABLED_MASK),
                            tu.setSignalHandler(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, ALL_SIGNALS_DISABLED_MASK),
                            tu.setSignalMask(SIGNAL_1_ENABLED_MASK),
                            tu.enableSignal(SIGNAL_NUMBER_2)),
                    tu.createRawTrace(
                            tu.switchThread(THREAD_1, ONE_SIGNAL),
                            tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                            tu.getSignalMask(SIGNALS_1_AND_2_ENABLED_MASK),
                            e1 = tu.nonAtomicStore(ADDRESS_2, VALUE_1),
                            tu.exitSignal())),
                Collections.singletonList(
                        tu.createRawTrace(
                                tu.switchThread(THREAD_1, NO_SIGNAL),
                                e2 = tu.nonAtomicStore(ADDRESS_2, VALUE_1))));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRaceMultipleWindows(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void signalsInterruptingTheSameThreadCannotOverlapWithPreviousSignals() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        when(mockConfiguration.desiredInterruptsPerSignalAndWindow()).thenReturn(10);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<RawTrace>> rawTraces = Arrays.asList(
                Arrays.asList(
                    tu.createRawTrace(
                            tu.disableSignal(SIGNAL_NUMBER_1),
                            tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                            tu.enableSignal(SIGNAL_NUMBER_1)),
                    tu.createRawTrace(
                            tu.switchThread(THREAD_1, ONE_SIGNAL),
                            tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                            e1 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                            tu.exitSignal())),
                Arrays.asList(
                        tu.createRawTrace(
                                false,
                                tu.switchThread(THREAD_1, NO_SIGNAL),
                                tu.disableSignal(SIGNAL_NUMBER_1),
                                tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                                tu.enableSignal(SIGNAL_NUMBER_1)),
                        tu.createRawTrace(
                                tu.switchThread(THREAD_1, ONE_SIGNAL),
                                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                                tu.exitSignal())
                ));

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertFalse(hasRaceMultipleWindows(rawTraces, event1, event2, tu, true));
    }

    @Test
    public void previousSignalFromThreadThatEndedCanInterruptFutureThread() throws InvalidTraceDataException {
        TraceUtils tu = new TraceUtils(mockContext, THREAD_1, NO_SIGNAL, BASE_PC);

        when(mockConfiguration.desiredInterruptsPerSignalAndWindow()).thenReturn(10);

        List<ReadonlyEventInterface> e1;
        List<ReadonlyEventInterface> e2;

        List<List<RawTrace>> rawTraces = Arrays.asList(
                Arrays.asList(
                        tu.createRawTrace(
                                tu.disableSignal(SIGNAL_NUMBER_1),
                                tu.nonAtomicLoad(ADDRESS_1, VALUE_1),
                                tu.enableSignal(SIGNAL_NUMBER_1)),
                        tu.createRawTrace(
                                tu.switchThread(THREAD_1, ONE_SIGNAL),
                                tu.enterSignal(SIGNAL_NUMBER_1, SIGNAL_HANDLER_1, GENERATION_1),
                                e1 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                                tu.exitSignal()),
                        tu.createRawTrace(
                                tu.switchThread(THREAD_2, NO_SIGNAL),
                                tu.threadJoin(THREAD_1))
                ),
                Collections.singletonList(
                        tu.createRawTrace(
                                false,
                                tu.switchThread(THREAD_2, NO_SIGNAL),
                                tu.enableSignal(SIGNAL_NUMBER_1),
                                e2 = tu.nonAtomicStore(ADDRESS_1, VALUE_1),
                                tu.exitSignal()))
                );

        ReadonlyEventInterface event1 = extractSingleEvent(e1);
        ReadonlyEventInterface event2 = extractSingleEvent(e2);
        Assert.assertTrue(hasRaceMultipleWindows(rawTraces, event1, event2, tu, true));
    }

    // TODO: Tests with writes that enable certain reads, both with and without signals.
    // TODO: Test that a signals stops their thread, i.e. it does not conflict with its own thread in a complex way,
    // i.e. it does not race with the interruption point, but it enables a subsequent read which allows one to
    // reach a racing instruction.
    // TODO: Test for signals using the same lock as the interrupted thread.
    // TODO: Test that signals that read a certain mask value (implicitly or explicitly) must run after that mask is
    // set.

    private boolean hasRace(
            List<RawTrace> rawTraces,
            ReadonlyEventInterface e1, ReadonlyEventInterface e2,
            TraceUtils tu) {
        return hasRace(rawTraces, e1, e2, tu, false);
    }

    private boolean hasRace(
            List<RawTrace> rawTraces,
            ReadonlyEventInterface e1, ReadonlyEventInterface e2,
            TraceUtils tu, boolean detectInterruptedThreadRace) {
        Map<String, Race> races = findRaces(
                Collections.singletonList(rawTraces),
                e1, e2,
                Collections.emptyList(),
                tu,
                detectInterruptedThreadRace,
                UNLIMITED_SIGNAL_DEPTH);
        return races.size() > 0;
    }

    private boolean hasRace(
            List<RawTrace> rawTraces,
            ReadonlyEventInterface e1, ReadonlyEventInterface e2,
            TraceUtils tu, boolean detectInterruptedThreadRace,
            int maxSignalDepth) {
        Map<String, Race> races = findRaces(
                Collections.singletonList(rawTraces),
                e1, e2,
                Collections.emptyList(),
                tu,
                detectInterruptedThreadRace,
                maxSignalDepth);
        return races.size() > 0;
    }

    private boolean hasRaceMultipleWindows(
            List<List<RawTrace>> rawTraces,
            ReadonlyEventInterface e1, ReadonlyEventInterface e2,
            TraceUtils tu,
            boolean detectInterruptedThreadRace) {
        Map<String, Race> races =
                findRaces(
                        rawTraces,
                        e1, e2,
                        Collections.emptyList(),
                        tu,
                        detectInterruptedThreadRace,
                        UNLIMITED_SIGNAL_DEPTH);
        return races.size() > 0;
    }

    private boolean hasRace(
            List<RawTrace> rawTraces,
            ReadonlyEventInterface e1, ReadonlyEventInterface e2,
            TraceUtils tu, List<ReadonlyEventInterface> previousSigestEvents,
            boolean detectInterruptedThreadRace) {
        Map<String, Race> races = findRaces(
                Collections.singletonList(rawTraces),
                e1, e2,
                previousSigestEvents,
                tu,
                detectInterruptedThreadRace,
                UNLIMITED_SIGNAL_DEPTH);
        return races.size() > 0;
    }


    private Map<String, Race> findRaces(
            List<List<RawTrace>> rawTracesList,
            ReadonlyEventInterface e1, ReadonlyEventInterface e2,
            List<ReadonlyEventInterface> previousSigestEvents,
            TraceUtils tu,
            boolean detectInterruptedThreadRace,
            int maxSignalDepth) {
        try (RaceSolver raceSolver = SingleThreadedRaceSolver.createRaceSolver(mockConfiguration)) {
            mockConfiguration.windowSize = WINDOW_SIZE;
            TraceState traceState = new TraceState(mockConfiguration, mockMetadata);
            ThreadInfos threadInfos = traceState.getThreadInfos();
            if (!previousSigestEvents.isEmpty()) {
                RawTrace rawTrace = tu.createRawTrace(false, previousSigestEvents);
                traceState.preStartWindow();
                threadInfos.registerThreadInfo(rawTrace.getThreadInfo());
                traceState.fastProcess(rawTrace);
            }

            Trace trace = null;
            assert !rawTracesList.isEmpty();
            for (List<RawTrace> rawTraces : rawTracesList) {
                for (RawTrace rawTrace : rawTraces) {
                    threadInfos.registerThreadInfo(rawTrace.getThreadInfo());
                }
                traceState.preStartWindow();
                trace = traceState.initNextTraceWindow(rawTraces);
            }
            MaximalCausalModel model =
                    MaximalCausalModel.create(trace, raceSolver, detectInterruptedThreadRace, maxSignalDepth);

            if (trace.getSize() == 0) {
                return Collections.emptyMap();
            }

            Map<String, List<Race>> sigToRaceSuspects = new HashMap<>();
            ArrayList<Race> raceSuspects = new ArrayList<>();
            raceSuspects.add(new Race(e1, e2, trace, mockConfiguration));
            sigToRaceSuspects.put("race", raceSuspects);

            return model.checkRaceSuspects(
                    sigToRaceSuspects, new AnalysisLimit(Clock.systemUTC(), "Test", Optional.empty(), 0, new Logger()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
