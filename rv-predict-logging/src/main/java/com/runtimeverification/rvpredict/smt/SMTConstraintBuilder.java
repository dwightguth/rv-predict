/*******************************************************************************
 * Copyright (c) 2013 University of Illinois
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.runtimeverification.rvpredict.smt;

import com.runtimeverification.rvpredict.log.EventItem;
import com.runtimeverification.rvpredict.smt.formula.*;
import com.runtimeverification.rvpredict.trace.EventType;
import com.runtimeverification.rvpredict.trace.LockRegion;
import com.runtimeverification.rvpredict.trace.Trace;
import java.util.Map;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.runtimeverification.rvpredict.config.Configuration;
import com.runtimeverification.rvpredict.util.Constants;

public class SMTConstraintBuilder {

    private final Trace trace;

    /**
     * Maps each event to its group ID in the contracted MHB graph.
     * <p>
     * The must-happens-before (MHB) relations form a special DAG where only a
     * few nodes have more than one outgoing edge. To speed up the reachability
     * query between two nodes, we first collapsed the DAG as much as possible.
     */
    private final int[] groupId;

    /**
     * Keeps track of the must-happens-before (MHB) relations in paper.
     */
    private final TransitiveClosure closure = new TransitiveClosure();

    private final LockSetEngine lockEngine = new LockSetEngine();

    private final Solver solver;

    private final Map<EventItem, Formula> abstractPhi = Maps.newHashMap();
    private final Map<EventItem, Formula> concretePhi = Maps.newHashMap();

    /**
     * Avoids infinite recursion when building the abstract feasibility
     * constraint of a {@link MemoryAccessEvent}.
     */
    private final Set<EventItem> computedAbstractPhi = Sets.newHashSet();

    /**
     * Avoids infinite recursion when building the concrete feasibility
     * constraint of a {@link MemoryAccessEvent}.
     */
    private final Set<EventItem> computedConcretePhi = Sets.newHashSet();

    // constraints below
    private final FormulaTerm.Builder smtlibAssertionBuilder = FormulaTerm.andBuilder();

    public SMTConstraintBuilder(Configuration config, Trace trace) {
        this.trace = trace;
        this.groupId = new int[trace.capacity()];
        this.solver = new Z3Wrapper(config);
    }

    private FormulaTerm getAsstHappensBefore(EventItem event1, EventItem event2) {
        return FormulaTerm.LESS_THAN(new OrderVariable(event1), new OrderVariable(event2));
    }

    private FormulaTerm getAsstLockRegionHappensBefore(LockRegion lockRegion1, LockRegion lockRegion2) {
        EventItem unlock = lockRegion1.getUnlock();
        EventItem lock = lockRegion2.getLock();
        return getAsstHappensBefore(
                unlock != null ? unlock : trace.getLastThreadEvent(lockRegion1.getThreadId()),
                lock != null ? lock : trace.getFirstThreadEvent(lockRegion2.getThreadId()));
    }

    private void assertMutualExclusion(LockRegion lockRegion1, LockRegion lockRegion2) {
        smtlibAssertionBuilder.add(FormulaTerm.OR(
                getAsstLockRegionHappensBefore(lockRegion1, lockRegion2),
                getAsstLockRegionHappensBefore(lockRegion2, lockRegion1)));
    }

    private int getRelativeIdx(EventItem event) {
        return (int) (event.getGID() % trace.capacity());
    }

    private int getGroupId(EventItem e) {
        return groupId[getRelativeIdx(e)];
    }

    private void setGroupId(EventItem e, int id) {
        groupId[getRelativeIdx(e)] = id;
    }

    /**
     * Adds program order constraints.
     */
    public void addIntraThreadConstraints() {
        for (List<EventItem> events : trace.getThreadIdToEventsMap().values()) {
            setGroupId(events.get(0), closure.nextElemId());
            for (int i = 1; i < events.size(); i++) {
                EventItem e1 = events.get(i - 1);
                EventItem e2 = events.get(i);
                smtlibAssertionBuilder.add(getAsstHappensBefore(e1, e2));
                if (e1.getType() == EventType.START ||
                    e2.getType() == EventType.START ||
                    i + 1 == events.size()) {
                    setGroupId(e2, closure.nextElemId());
                    closure.addRelation(getGroupId(e1), getGroupId(e2));
                } else {
                    setGroupId(e2, getGroupId(e1));
                }
            }
        }
    }

    /**
     * Adds thread start/join constraints.
     */
    public void addThreadStartJoinConstraints() {
        for (List<EventItem> startOrJoinEvents : trace.getThreadIdToStartJoinEvents().values()) {
            for (EventItem startOrJoin : startOrJoinEvents) {
                long tid = startOrJoin.getSyncObject();
                switch (startOrJoin.getType()) {
                case START:
                    EventItem startEvent = startOrJoin;
                    EventItem fstThrdEvent = trace.getFirstThreadEvent(tid);
                    /* YilongL: it's possible that the first event of the new
                     * thread is not in the current trace */
                    if (fstThrdEvent != null) {
                        smtlibAssertionBuilder.add(getAsstHappensBefore(startEvent, fstThrdEvent));
                        closure.addRelation(getGroupId(startEvent), getGroupId(fstThrdEvent));
                    }
                    break;
                case JOIN:
                    EventItem joinEvent = startOrJoin;
                    EventItem lastThrdEvent = trace.getLastThreadEvent(tid);
                    /* YilongL: it's possible that the last event of the thread
                     * to join is not in the current trace */
                    if (lastThrdEvent != null) {
                        smtlibAssertionBuilder.add(getAsstHappensBefore(lastThrdEvent, joinEvent));
                        closure.addRelation(getGroupId(lastThrdEvent), getGroupId(joinEvent));
                    }
                    break;
                default:
                    assert false : "unexpected event: " + startOrJoin;
                }
            }
        }
    }

    /**
     * Adds lock mutual exclusion constraints.
     */
    public void addLockingConstraints() {
        /* enumerate the locking events on each lock */
        for (List<EventItem> syncEvents : trace.getLockObjToSyncEvents().values()) {
            Map<Long, EventItem> threadIdToPrevLockOrUnlock = Maps.newHashMap();
            List<LockRegion> lockRegions = Lists.newArrayList();

            for (EventItem syncEvent : syncEvents) {
                long tid = syncEvent.getTID();
                EventItem prevLockOrUnlock = threadIdToPrevLockOrUnlock.get(tid);
                assert prevLockOrUnlock == null
                    || !(prevLockOrUnlock.doLock() && syncEvent.doLock())
                    || !(prevLockOrUnlock.doUnlock() && syncEvent.doUnlock()) :
                    "Unexpected consecutive lock/unlock events:\n" + prevLockOrUnlock + ", " + syncEvent;

                switch (syncEvent.getType()) {
                case WRITE_LOCK:
                case READ_LOCK:
                case WAIT_ACQ:
                    threadIdToPrevLockOrUnlock.put(tid, syncEvent);
                    break;

                case WRITE_UNLOCK:
                case READ_UNLOCK:
                case WAIT_REL:
                    lockRegions.add(new LockRegion(threadIdToPrevLockOrUnlock.put(tid, syncEvent),
                            syncEvent));
                    break;
                default:
                    assert false : "Unexpected synchronization event: " + syncEvent;
                }
            }

            for (EventItem lockOrUnlock : threadIdToPrevLockOrUnlock.values()) {
                if (lockOrUnlock.doLock()) {
                    EventItem lock = lockOrUnlock;
                    lockRegions.add(new LockRegion(lock, null));
                }
            }

            lockEngine.addAll(lockRegions);

            /* assert lock regions mutual exclusion */
            assertLockMutex(lockRegions);
        }
    }

    private void assertLockMutex(List<LockRegion> lockRegions) {
        for (LockRegion lockRegion1 : lockRegions) {
            for (LockRegion lockRegion2 : lockRegions) {
                if (lockRegion1.getThreadId() < lockRegion2.getThreadId()
                        && (lockRegion1.isWriteLocked() || lockRegion2.isWriteLocked())) {
                    assertMutualExclusion(lockRegion1, lockRegion2);
                }
            }
        }
    }

    /**
     * Generates a formula ensuring that all read events that {@code event}
     * depends on read the same value as in the original trace, to guarantee
     * {@code event} will be generated in the predicted trace. Note that,
     * however, this {@code event} is allowed to read or write a different value
     * than in the original trace.
     */
    public Formula getAbstractFeasibilityConstraint(EventItem event) {
        if (computedAbstractPhi.contains(event)) {
            return new AbstractPhiVariable(event);
        }
        computedAbstractPhi.add(event);

        FormulaTerm.Builder phiBuilder = FormulaTerm.andBuilder();
        /* make sure that every dependent read event reads the same value as in the original trace */
        for (EventItem depRead : trace.getCtrlFlowDependentEvents(event)) {
            phiBuilder.add(getConcreteFeasibilityConstraint(depRead));
        }
        abstractPhi.put(event, phiBuilder.build());
        return new AbstractPhiVariable(event);
    }

    /**
     * Generates a formula ensuring that {@code event} will be generated exactly
     * the same as in the original trace <b>if</b> the corresponding data-abstract
     * feasibility constraint is also satisfied.
     */
    private Formula getConcreteFeasibilityConstraint(EventItem event) {
        if (computedConcretePhi.contains(event)) {
            return new ConcretePhiVariable(event);
        } else if (event.getValue() == Constants._0X_DEADBEEFL) {
            return BooleanConstant.TRUE;
        }
        computedConcretePhi.add(event);

        Formula phi;
        if (event.isRead()) {
            List<EventItem> writeEvents = trace.getWriteEventsOn(event.getAddr());

            /* thread immediate write predecessor */
            EventItem thrdImdWrtPred = null;
            /* predecessor write set: all write events whose values could be read by `depRead' */
            List<EventItem> predWriteSet = Lists.newArrayList();
            for (EventItem write : writeEvents) {
                if (write.getTID() == event.getTID()) {
                    if (write.getGID() < event.getGID()) {
                        thrdImdWrtPred = write;
                    }
                } else if (!happensBefore(event, write)) {
                    predWriteSet.add(write);
                }
            }
            if (thrdImdWrtPred != null) {
                predWriteSet.add(thrdImdWrtPred);
            }

            /* predecessor write set of same value */
            List<EventItem> sameValPredWriteSet = Lists.newArrayList();
            for (EventItem write : predWriteSet) {
                if (write.getValue() == event.getValue()) {
                    sameValPredWriteSet.add(write);
                }
            }

            /* case 1: the dependent read reads the initial value */
            Formula case1 = BooleanConstant.FALSE;
            if (thrdImdWrtPred == null &&
                    trace.getInitValueOf(event.getAddr()) == event.getValue()) {
                FormulaTerm.Builder formulaBuilder = FormulaTerm.andBuilder();
                for (EventItem write : predWriteSet) {
                    formulaBuilder.add(getAsstHappensBefore(event, write));
                }
                case1 = formulaBuilder.build();
            }

            /* case 2: the dependent read reads a previously written value */
            FormulaTerm.Builder case2Builder = FormulaTerm.orBuilder();
            for (EventItem write : sameValPredWriteSet) {
                FormulaTerm.Builder formulaBuilder = FormulaTerm.andBuilder();
                formulaBuilder.add(getAbstractFeasibilityConstraint(write),
                        getConcreteFeasibilityConstraint(write));
                formulaBuilder.add(getAsstHappensBefore(write, event));
                for (EventItem otherWrite : writeEvents) {
                    if (write != otherWrite && !happensBefore(otherWrite, write)
                            && !happensBefore(event, otherWrite)) {
                        formulaBuilder.add(FormulaTerm.OR(
                                getAsstHappensBefore(otherWrite, write),
                                getAsstHappensBefore(event, otherWrite)));
                    }
                }
                case2Builder.add(formulaBuilder.build());
            }
            phi = FormulaTerm.OR(case1, case2Builder.build());
        } else {
            phi = BooleanConstant.TRUE;
        }
        concretePhi.put(event, phi);

        return new ConcretePhiVariable(event);
    }

    /**
     * Checks if two {@code MemoryAccessEvent} hold a common lock.
     */
    public boolean hasCommonLock(EventItem e1, EventItem e2) {
        return lockEngine.hasCommonLock(e1, e2);
    }

    /**
     * Checks if one event happens before another.
     */
    public boolean happensBefore(EventItem e1, EventItem e2) {
        return closure.inRelation(getGroupId(e1), getGroupId(e2));
    }

    public boolean isRace(EventItem e1, EventItem e2, Formula... casualConstraints) {
        FormulaTerm.Builder raceAssertionBuilder = FormulaTerm.andBuilder();
        raceAssertionBuilder.add(smtlibAssertionBuilder.build());
        for (Entry<EventItem, Formula> entry : abstractPhi.entrySet()) {
            raceAssertionBuilder.add(FormulaTerm.BOOL_EQUAL(
                    new AbstractPhiVariable(entry.getKey()),
                    entry.getValue()));
        }
        for (Entry<EventItem, Formula> entry : concretePhi.entrySet()) {
            raceAssertionBuilder.add(FormulaTerm.BOOL_EQUAL(
                    new ConcretePhiVariable(entry.getKey()),
                    entry.getValue()));
        }
        raceAssertionBuilder.add(FormulaTerm
                .INT_EQUAL(new OrderVariable(e1), new OrderVariable(e2)));
        for (Formula casualConstraint : casualConstraints) {
            raceAssertionBuilder.add(casualConstraint);
        }

        return solver.isSat(raceAssertionBuilder.build());
    }

    public void finish() {
        closure.finish();
    }

}
