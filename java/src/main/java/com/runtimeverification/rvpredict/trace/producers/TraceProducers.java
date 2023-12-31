package com.runtimeverification.rvpredict.trace.producers;

import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;
import com.runtimeverification.rvpredict.producerframework.ComputingProducerWrapper;
import com.runtimeverification.rvpredict.producerframework.LeafProducerWrapper;
import com.runtimeverification.rvpredict.producerframework.ProducerModule;
import com.runtimeverification.rvpredict.producerframework.ProducerState;
import com.runtimeverification.rvpredict.signals.SignalMask;
import com.runtimeverification.rvpredict.trace.RawTrace;
import com.runtimeverification.rvpredict.trace.ThreadInfos;
import com.runtimeverification.rvpredict.trace.producers.base.DesiredThreadCountForSignal;
import com.runtimeverification.rvpredict.trace.producers.base.InterThreadSyncEvents;
import com.runtimeverification.rvpredict.trace.producers.base.MinEventIdForWindow;
import com.runtimeverification.rvpredict.trace.producers.base.OtidToMainTtid;
import com.runtimeverification.rvpredict.trace.producers.base.OtidToSignalDepthToTtidAtWindowStart;
import com.runtimeverification.rvpredict.trace.producers.base.PreviousSignalsTraceMerger;
import com.runtimeverification.rvpredict.trace.producers.base.RawTraces;
import com.runtimeverification.rvpredict.trace.producers.base.RawTracesByTtid;
import com.runtimeverification.rvpredict.trace.producers.base.SortedTtidsWithParentFirst;
import com.runtimeverification.rvpredict.trace.producers.base.StackTraces;
import com.runtimeverification.rvpredict.trace.producers.base.StackTracesLeaf;
import com.runtimeverification.rvpredict.trace.producers.base.ThreadInfosComponent;
import com.runtimeverification.rvpredict.trace.producers.base.TtidSetDifference;
import com.runtimeverification.rvpredict.trace.producers.base.TtidSetLeaf;
import com.runtimeverification.rvpredict.trace.producers.base.TtidToStartAndJoinEventsForWindow;
import com.runtimeverification.rvpredict.trace.producers.signals.InterruptedEvents;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalEnabledAtStartInferenceFromInterruptions;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalEnabledAtStartInferenceFromReads;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalEnabledAtStartInferenceTransitiveClosure;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalMaskAtWindowOrThreadStartWithInferences;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalMaskAtWindowStart;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalMaskAtWindowStartLeaf;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalMaskAtWindowStartWithoutInferences;
import com.runtimeverification.rvpredict.trace.producers.signals.SignalMaskForEvents;
import com.runtimeverification.rvpredict.trace.producers.signals.ThreadsWhereSignalIsEnabled;
import com.runtimeverification.rvpredict.trace.producers.signals.TtidsToSignalEnabling;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

// pw as a prefix means that a given producer works with traces from the previous windows which may be added to the
// current one.
// 'merged' as a prefix means that a given producer works with traces both from the previous windows and from the
// current one.
public class TraceProducers extends ProducerModule {
    private final LeafProducerWrapper<List<RawTrace>, RawTraces> currentWindowRawTraces =
            new LeafProducerWrapper<>(new RawTraces(), this);
    private final LeafProducerWrapper<List<RawTrace>, RawTraces> pwSignalsRawTraces =
            new LeafProducerWrapper<>(new RawTraces(), this);
    private final LeafProducerWrapper<Integer, DesiredThreadCountForSignal> desiredThreadCountForSignal =
            new LeafProducerWrapper<>(new DesiredThreadCountForSignal(), this);
    private final LeafProducerWrapper<Set<Integer>, TtidSetLeaf> ttidsForCurrentWindow =
            new LeafProducerWrapper<>(new TtidSetLeaf(), this);
    public final LeafProducerWrapper<ThreadInfos, ThreadInfosComponent> threadInfosComponent =
            new LeafProducerWrapper<>(new ThreadInfosComponent(), this);
    private final LeafProducerWrapper<Map<Integer, SignalMask>,  SignalMaskAtWindowStartLeaf>
            signalMaskAtWindowStartLeaf =
            new LeafProducerWrapper<>(new SignalMaskAtWindowStartLeaf(), this);
    public final LeafProducerWrapper<Map<Long, Integer>, OtidToMainTtid> otidToMainTtid =
            new LeafProducerWrapper<>(new OtidToMainTtid(), this);
    private final LeafProducerWrapper<Set<Integer>, TtidSetLeaf> ttidsStartedAtWindowStart =
            new LeafProducerWrapper<>(new TtidSetLeaf(), this);
    private final LeafProducerWrapper<Set<Integer>, TtidSetLeaf> ttidsStartedAtWindowEnd =
            new LeafProducerWrapper<>(new TtidSetLeaf(), this);
    private final LeafProducerWrapper<Set<Integer>, TtidSetLeaf> ttidsFinishedAtWindowStart =
            new LeafProducerWrapper<>(new TtidSetLeaf(), this);
    private final LeafProducerWrapper<Set<Integer>, TtidSetLeaf> ttidsFinishedAtWindowEnd =
            new LeafProducerWrapper<>(new TtidSetLeaf(), this);
    private final LeafProducerWrapper<Map<Integer, Deque<ReadonlyEventInterface>>, StackTracesLeaf> startStackTraces =
            new LeafProducerWrapper<>(new StackTracesLeaf(), this);

    public final ComputingProducerWrapper<InterThreadSyncEvents> interThreadSyncEvents =
            new ComputingProducerWrapper<>(new InterThreadSyncEvents(currentWindowRawTraces), this);
    public final ComputingProducerWrapper<TtidToStartAndJoinEventsForWindow> startAndJoinEventsForWindow =
            new ComputingProducerWrapper<>(
                    new TtidToStartAndJoinEventsForWindow(interThreadSyncEvents, otidToMainTtid), this);
    private final ComputingProducerWrapper<TtidSetDifference> threadStartsInTheCurrentWindow =
            new ComputingProducerWrapper<>(
                    new TtidSetDifference(ttidsStartedAtWindowEnd, ttidsStartedAtWindowStart),
                    this);
    private final ComputingProducerWrapper<TtidSetDifference> threadEndsInTheCurrentWindow =
            new ComputingProducerWrapper<>(
                    new TtidSetDifference(ttidsFinishedAtWindowEnd, ttidsFinishedAtWindowStart),
                    this);
    private final ComputingProducerWrapper<TtidSetDifference> unfinishedTtidsAtWindowStart =
            new ComputingProducerWrapper<>(
                    new TtidSetDifference(ttidsStartedAtWindowStart, ttidsFinishedAtWindowStart),
                    this);
    private final ComputingProducerWrapper<OtidToSignalDepthToTtidAtWindowStart> otidToSignalDepthToTtidAtWindowStart =
            new ComputingProducerWrapper<>(
                    new OtidToSignalDepthToTtidAtWindowStart(unfinishedTtidsAtWindowStart, threadInfosComponent),
                    this);
    private final ComputingProducerWrapper<SignalMaskAtWindowStart<? extends ProducerState>>
            signalMaskAtWindowStartWithoutInferences =
            new ComputingProducerWrapper<>(
                    new SignalMaskAtWindowStartWithoutInferences(signalMaskAtWindowStartLeaf), this);
    public final ComputingProducerWrapper<MinEventIdForWindow> minEventIdForWindow =
            new ComputingProducerWrapper<>(new MinEventIdForWindow(currentWindowRawTraces), this);
    private final ComputingProducerWrapper<RawTracesByTtid> rawTracesByTtid =
            new ComputingProducerWrapper<>(new RawTracesByTtid(currentWindowRawTraces), this);
    private final ComputingProducerWrapper<SortedTtidsWithParentFirst> sortedTtidsWithParentFirst =
            new ComputingProducerWrapper<>(
                    new SortedTtidsWithParentFirst(ttidsForCurrentWindow, threadInfosComponent),
                    this);
    private final ComputingProducerWrapper<InterruptedEvents > interruptedEvents =
            new ComputingProducerWrapper<>(
                    new InterruptedEvents(
                            rawTracesByTtid, ttidsForCurrentWindow, threadInfosComponent,
                            threadStartsInTheCurrentWindow, threadEndsInTheCurrentWindow,
                            minEventIdForWindow),
                    this);
    private final ComputingProducerWrapper<SignalMaskForEvents> signalMaskForEventsWithoutInferences =
            new ComputingProducerWrapper<>(
                    new SignalMaskForEvents(
                            rawTracesByTtid,
                            sortedTtidsWithParentFirst,
                            signalMaskAtWindowStartWithoutInferences,
                            otidToMainTtid,
                            interruptedEvents, threadInfosComponent),
                    this);
    private final ComputingProducerWrapper<SignalEnabledAtStartInferenceFromInterruptions>
            signalEnabledAtStartInferrenceFromInterruptions =
            new ComputingProducerWrapper<>(
                    new SignalEnabledAtStartInferenceFromInterruptions(
                            interruptedEvents,
                            signalMaskForEventsWithoutInferences,
                            unfinishedTtidsAtWindowStart,
                            threadInfosComponent,
                            otidToSignalDepthToTtidAtWindowStart),
                    this);
    private final ComputingProducerWrapper<SignalEnabledAtStartInferenceFromReads>
            signalEnabledAtStartInferenceFromReads =
            new ComputingProducerWrapper<>(
                    new SignalEnabledAtStartInferenceFromReads(
                            currentWindowRawTraces,
                            signalMaskForEventsWithoutInferences),
                    this);
    private final ComputingProducerWrapper<SignalEnabledAtStartInferenceTransitiveClosure>
            signalEnabledAtStartInferenceTransitiveClosure =
            new ComputingProducerWrapper<>(
                    new SignalEnabledAtStartInferenceTransitiveClosure(
                            signalEnabledAtStartInferenceFromReads,
                            signalEnabledAtStartInferrenceFromInterruptions,
                            sortedTtidsWithParentFirst,
                            threadInfosComponent,
                            interruptedEvents,
                            signalMaskForEventsWithoutInferences,
                            signalMaskAtWindowStartWithoutInferences,
                            startAndJoinEventsForWindow),
                    this);
    private final ComputingProducerWrapper<SignalMaskAtWindowOrThreadStartWithInferences>
            signalMaskAtWindowStartWithInferences =
            new ComputingProducerWrapper<>(
                    new SignalMaskAtWindowOrThreadStartWithInferences(
                            signalMaskAtWindowStartWithoutInferences,
                            signalEnabledAtStartInferenceTransitiveClosure,
                            ttidsForCurrentWindow),
                    this);
    // TODO: this should also contain the merged ones, otherwise we can't interrupt previous window signals.
    public final ComputingProducerWrapper<SignalMaskForEvents> signalMaskForEvents =
            new ComputingProducerWrapper<>(
                    new SignalMaskForEvents(
                            rawTracesByTtid,
                            sortedTtidsWithParentFirst,
                            signalMaskAtWindowStartWithInferences,
                            otidToMainTtid,
                            interruptedEvents, threadInfosComponent),
                    this);
    public final ComputingProducerWrapper<TtidsToSignalEnabling> ttidsToSignalEnabling =
            new ComputingProducerWrapper<>(
                    new TtidsToSignalEnabling(signalMaskAtWindowStartWithInferences),
                    this);
    public final ComputingProducerWrapper<ThreadsWhereSignalIsEnabled> threadsWhereSignalIsEnabled =
            new ComputingProducerWrapper<>(
                    new ThreadsWhereSignalIsEnabled(
                            signalMaskForEvents,
                            ttidsForCurrentWindow,
                            rawTracesByTtid,
                            signalMaskAtWindowStartWithInferences),
                    this);

    public final ComputingProducerWrapper<PreviousSignalsTraceMerger> mergedRawTraces =
            new ComputingProducerWrapper<>(
                    new PreviousSignalsTraceMerger(
                            currentWindowRawTraces, pwSignalsRawTraces,
                            threadsWhereSignalIsEnabled,
                            desiredThreadCountForSignal),
                    this);

    public final ComputingProducerWrapper<StackTraces> stackTraces =
            new ComputingProducerWrapper<>(
                    new StackTraces(mergedRawTraces, startStackTraces),
                    this);

    public void startWindow(
            List<RawTrace> previousSignalsRawTraces, List<RawTrace> currentWindowRawTraces,
            Set<Integer> threadsForCurrentWindow, ThreadInfos threadInfos,
            Map<Integer, SignalMask> signalMaskAtWindowStart,
            Set<Integer> ttidsStartedAtWindowStart, Set<Integer> ttidsStartedAtWindowEnd,
            Set<Integer> ttidsFinishedAtWindowStart, Set<Integer> ttidsFinishedAtWindowEnd,
            Map<Integer, Deque<ReadonlyEventInterface>> startStackTraces,
            int desiredThreadCountForSignal) {
        reset();
        this.pwSignalsRawTraces.set(previousSignalsRawTraces);
        this.currentWindowRawTraces.set(currentWindowRawTraces);
        this.ttidsForCurrentWindow.set(threadsForCurrentWindow);
        this.threadInfosComponent.set(threadInfos);
        this.signalMaskAtWindowStartLeaf.set(signalMaskAtWindowStart);
        this.otidToMainTtid.set(threadInfos.getOtidToTtid());
        this.ttidsStartedAtWindowStart.set(ttidsStartedAtWindowStart);
        this.ttidsStartedAtWindowEnd.set(ttidsStartedAtWindowEnd);
        this.ttidsFinishedAtWindowStart.set(ttidsFinishedAtWindowStart);
        this.ttidsFinishedAtWindowEnd.set(ttidsFinishedAtWindowEnd);
        this.startStackTraces.set(startStackTraces);
        this.desiredThreadCountForSignal.set(desiredThreadCountForSignal);
    }
}
