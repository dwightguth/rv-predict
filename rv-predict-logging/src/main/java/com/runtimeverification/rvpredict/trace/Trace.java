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
package com.runtimeverification.rvpredict.trace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

import com.google.common.collect.*;
import com.runtimeverification.rvpredict.config.Configuration;
import com.runtimeverification.rvpredict.log.Event;
import com.runtimeverification.rvpredict.log.EventType;
import com.runtimeverification.rvpredict.metadata.Metadata;
import com.runtimeverification.rvpredict.util.Constants;

/**
 * Representation of the execution trace. Each event is created as a node with a
 * corresponding type. Events are indexed by their thread Id, Type, and memory
 * address.
 */
public class Trace {

    private final int numOfEvents;

    private final long minGID;

    /**
     * Read threads on each address. Only used in filtering thread-local
     * {@link MemoryAccessEvent}s.
     */
    private final Map<MemoryAddr, Set<Long>> addrToReadThreads = new HashMap<>();

    /**
     * Write threads on each address. Only used in filtering thread-local
     * {@link MemoryAccessEvent}s.
     */
    private final Map<MemoryAddr, Set<Long>> addrToWriteThreads = new HashMap<>();

    /**
     * Events of each thread.
     */
    private final Map<Long, List<Event>> threadIdToEvents = new HashMap<>();

    /**
     * List of START/JOIN events in this window.
     */
    private final List<Event> startJoinEvents = new ArrayList<>();

    /**
     * Wait/Lock/Unlock events indexed by the involved lock object.
     */
    private final Map<Long, List<Event>> lockIdToLockEvents = new HashMap<>();

    /**
     * Write events on each address.
     */
    private final Map<MemoryAddr, List<Event>> addrToWriteEvents = new HashMap<>();

    /**
     * Lists of {@code MemoryAccessEvent}'s indexed by address and thread ID.
     */
    private final Table<MemoryAddr, Long, List<Event>> memAccessEventsTbl = HashBasedTable.create();

    /**
     * The initial value at all addresses referenced in this trace segment. It
     * is computed as the value in the {@link #state} before the first
     * access occurring in this trace segment.
     */
    private final Map<MemoryAddr, Long> addrToInitValue = Maps.newHashMap();

    /**
     * The initial stack trace for all threads referenced in this trace segment.
     * It is computed as the value in the {@link #state} before the first
     * event of that thread occurring in this trace segment.
     */
    private final Map<Long, ThreadStatus> threadIdToInitThreadStatus = Maps.newHashMap();

    private final Map<Event, List<Integer>> initHeldLockToStacktrace;

    /**
     * Set of {@code MemoryAccessEvent}'s that happen during class initialization.
     */
    private final Set<Event> clinitEvents = Sets.newHashSet();

    /**
     * Set of outermost LOCK/UNLOCK events.
     */
    private final Set<Event> outermostLockingEvents = Sets.newHashSet();

    /**
     * Map from thread Id to {@link EventType#INVOKE_METHOD} and
     * {@link EventType#FINISH_METHOD} events.
     */
    private final Map<Long, List<Event>> threadIdToCallStackEvents = Maps.newHashMap();

    private final Metadata metadata;

    /**
     * Maintains the current values for every location, as recorded into the trace
     */
    private final TraceState state;

    public Trace(TraceState crntState, Event[] events, int numOfEvents) {
        this.state = crntState;
        this.metadata = crntState.metadata();
        this.initHeldLockToStacktrace = crntState.getHeldLockStacktraceSnapshot();
        this.numOfEvents = numOfEvents;
        this.minGID = numOfEvents > 0 ? events[0].getGID() : -1;
        for (int i = 0; i < numOfEvents; i++) {
            // TODO(YilongL): avoid doing copy here
            process(events[i].copy());
        }
        finalize(events);
    }

    public Metadata metadata() {
        return metadata;
    }

    public int getSize() {
        return numOfEvents;
    }

    public boolean mayContainRaces() {
        // This method can be further improved to skip an entire window ASAP
        // For example, if this window contains no race candidate determined
        // by some static analysis then we can safely skip it
        return !memAccessEventsTbl.isEmpty();
    }

    /**
     * Gets the initial value of a memory address.
     *
     * @param addr
     *            the address
     * @return the initial value
     */
    public Long getInitValueOf(MemoryAddr addr) {
        Long initValue = addrToInitValue.get(addr);
        assert initValue != null : "All values in the trace should have been set in addRawEvent";
        return initValue;
    }

    public Event getFirstEvent(long tid) {
        List<Event> events = threadIdToEvents.get(tid);
        return events == null ? null : events.get(0);
    }

    public Event getLastEvent(long tid) {
        List<Event> events = threadIdToEvents.get(tid);
        return events == null ? null : events.get(events.size() - 1);
    }

    public List<Event> getEvents(long tid) {
        List<Event> events = threadIdToEvents.get(tid);
        return events == null ? Lists.<Event>newArrayList() : events;
    }

    public Collection<List<Event>> perThreadView() {
        return threadIdToEvents.values();
    }

    public List<Event> getStartJoinEvents() {
        return startJoinEvents;
    }

    public Collection<List<Event>> getLockEventsCollection() {
        return lockIdToLockEvents.values();
    }

    public List<Event> getWriteEventsOn(MemoryAddr addr) {
        List<Event> events = addrToWriteEvents.get(addr);
        return events == null ? Lists.<Event>newArrayList() : events;
    }

    public Set<MemoryAddr> getMemoryAddresses() {
        return memAccessEventsTbl.rowKeySet();
    }

    public Table<MemoryAddr, Long, List<Event>> getMemAccessEventsTable() {
        return memAccessEventsTbl;
    }

    public boolean isInsideClassInitializer(Event event) {
        return clinitEvents.contains(event);
    }

    /**
     * Returns the {@code String} representation of the stack trace at the point
     * when a given {@code event} happened.
     *
     * @param event
     *            the event
     * @return a {@code List} of stack trace element represented as location
     *         ID's; {@code -1} represents missing stack trace elements
     */
    public List<Integer> getStacktraceAt(Event event) {
        long tid = event.getTID();
        List<Integer> stacktrace = Lists.newArrayList();
        if (event.getGID() >= minGID) {
            /* event is in the current window; reassemble its stack trace */
            for (int locId : threadIdToInitThreadStatus.get(tid).stacktrace) {
                stacktrace.add(locId);
            }
            for (Event e : threadIdToCallStackEvents.getOrDefault(tid,
                    Collections.<Event> emptyList())) {
                if (e.getGID() >= event.getGID()) {
                    break;
                }

                if (e.getType() == EventType.INVOKE_METHOD) {
                    stacktrace.add(e.getLocId());
                } else {
                    stacktrace.remove(stacktrace.size() - 1);
                }
            }
            stacktrace.add(event.getLocId());
        } else {
            /* event is from previous windows */
            if (initHeldLockToStacktrace.containsKey(event)) {
                stacktrace.addAll(initHeldLockToStacktrace.get(event));
            } else {
                stacktrace.add(event.getLocId());
                stacktrace.add(-1);
            }
        }
        return stacktrace;
    }

    public List<LockObject> getHeldLocksAt(Event memAcc) {
        long tid = memAcc.getTID();
        Map<Long, Deque<Event>> map = Maps.newHashMap();
        for (Map.Entry<Long, List<Event>> entry : threadIdToInitThreadStatus.get(tid).lockStatus
                .entrySet()) {
            map.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
        }
        for (Event e : getEvents(tid)) {
            if (e.getGID() >= memAcc.getGID()) {
                break;
            }

            EventType type = e.getType();
            if (type.isLockType()) {
                long lockId = e.getSyncObject();
                map.putIfAbsent(lockId, new ArrayDeque<Event>());
                map.get(lockId).add(e);
            } else if (type.isUnlockType()) {
                long lockId = e.getSyncObject();
                Event lock = map.get(lockId).removeLast();
                assert lock.getTID() == tid && lock.getSyncObject() == lockId;
            }
        }

        List<LockObject> lockObjects = Lists.newArrayList();
        for (Deque<Event> deque : map.values()) {
            if (!deque.isEmpty()) {
                lockObjects.add(LockObject.create(deque.peek()));
            }
        }
        Collections.sort(lockObjects, new Comparator<LockObject>() {
            @Override
            public int compare(LockObject o1, LockObject o2) {
                return Long.compare(o1.getLockEvent().getGID(), o2.getLockEvent().getGID());
            }
        });
        return lockObjects;
    }

    public Event getStartEventOf(long tid) {
        return state.getThreadStartEvent(tid);
    }

    /**
     * Gets control-flow dependent events of a given {@code MemoryAccessEvent}.
     * Without any knowledge about the control flow of the program, all read
     * events that happen-before the given event have to be included
     * conservatively.
     */
    public List<Event> getCtrlFlowDependentEvents(Event memAccEvent) {
        List<Event> readEvents = new ArrayList<>();
        for (Event e : getEvents(memAccEvent.getTID())) {
            if (e.getGID() >= memAccEvent.getGID()) {
                break;
            }

            if (e.isRead()) {
                readEvents.add(e);
            }
        }

        return readEvents;
    }

    private void process(Event event) {
//        System.err.println(event + " at " + metadata.getLocationSig(event.getLocId()));
        updateTraceState(event);

        if (event.isReadOrWrite()) {
            getOrInitEmptySet(event.isRead() ? addrToReadThreads : addrToWriteThreads,
                    event.getAddr()).add(event.getTID());
        }
    }

    private void updateTraceState(Event event) {
        long tid = event.getTID();
        if (!threadIdToInitThreadStatus.containsKey(tid)) {
            ThreadStatus initThreadInfo = getCurrentThreadStatus(tid);
            threadIdToInitThreadStatus.putIfAbsent(tid, initThreadInfo);
        }

        if (event.isReadOrWrite()) {
            MemoryAddr addr = event.getAddr();
            long value = event.getValue();
            long crntVal = state.getValueAt(addr);
            addrToInitValue.putIfAbsent(addr, crntVal);
            if (event.isRead()) {
                if (value != Constants._0X_DEADBEEFL && value != crntVal) {
                    if (Configuration.debug) {
                        System.err.printf(
                            String.format("[Warning] logged trace not sequential consistent:%n"
                                    + "  event %s reads a different value than the currently stored value %s%n"
                                    + "    at %s%n",
                                    event, crntVal, metadata.getLocationSig(event.getLocId())));
                    }
                    state.writeValueAt(addr, crntVal);
                }
            } else {
                state.writeValueAt(addr, event.getValue());
            }

            if (state.isInsideClassInitializer(tid)) {
                clinitEvents.add(event);
            }
        } else if (event.isThreadStart()) {
            state.onThreadStart(event);
        } else if (event.getType().isLockType() || event.getType().isUnlockType()) {
            long lockId = event.getSyncObject();
            if (event.getType().isLockType()) {
                state.acquireLock(event);
                if (state.getLockCount(tid, lockId) == 1) {
                    outermostLockingEvents.add(event);
                }
            } else {
                state.releaseLock(event);
                if (state.getLockCount(tid, lockId) == 0) {
                    outermostLockingEvents.add(event);
                }
            }
        } else if (event.isMetaEvent()) {
            EventType eventType = event.getType();
            if (eventType == EventType.CLINIT_ENTER) {
                state.incClinitDepth(tid);
            } else if (eventType == EventType.CLINIT_EXIT) {
                state.decClinitDepth(tid);
            } else if (eventType == EventType.INVOKE_METHOD) {
                state.invokeMethod(event);
                getOrInitEmptyList(threadIdToCallStackEvents, tid).add(event);
            } else if (eventType == EventType.FINISH_METHOD) {
                state.finishMethod(event);
                getOrInitEmptyList(threadIdToCallStackEvents, tid).add(event);
            } else {
                assert false : "unreachable";
            }
        }
    }

    private ThreadStatus getCurrentThreadStatus(long tid) {
        return new ThreadStatus(state.getStacktraceSnapshot(tid),
                state.getLockStatusSnapshot(tid));
    }

    /**
     * add a new filtered event to the trace in the order of its appearance
     *
     * @param event
     */
    private void addEvent(Event event) {
//        System.err.println(event + " at " + metadata.getLocationSig(event.getLocId()));
        long tid = event.getTID();

        getOrInitEmptyList(threadIdToEvents, tid).add(event);
        if (event.isReadOrWrite()) {
            MemoryAddr addr = event.getAddr();

            getOrInitEmptyList(memAccessEventsTbl.row(addr), tid).add(event);

            if (event.isWrite()) {
                getOrInitEmptyList(addrToWriteEvents, addr).add(event);
            }
        } else if (event.isSyncEvent()) {
            switch (event.getType()) {
            case START:
            case JOIN:
                startJoinEvents.add(event);
                break;
            case WRITE_LOCK:
            case WRITE_UNLOCK:
            case READ_LOCK:
            case READ_UNLOCK:
            case WAIT_REL:
            case WAIT_ACQ:
                getOrInitEmptyList(lockIdToLockEvents, event.getSyncObject()).add(event);
                break;
            default:
                assert false : "unexpected event: " + event;
            }

        } else {
            assert false : "unreachable";
        }
    }

    /**
     * Once trace is completely loaded, remove local data accesses and process
     * the remaining trace.
     */
    private void finalize(Event[] events) {
        /* compute memory addresses that are accessed by more than one thread
         * and with at least one write access */
        Set<MemoryAddr> sharedMemAddr = new HashSet<>();
        for (MemoryAddr addr : Iterables.concat(addrToReadThreads.keySet(),
                addrToWriteThreads.keySet())) {
            Set<Long> wrtThrdIds = addrToWriteThreads.get(addr);
            if (wrtThrdIds != null && !wrtThrdIds.isEmpty()) {
                if (wrtThrdIds.size() > 1) {
                    sharedMemAddr.add(addr);
                } else {
                    Set<Long> readThrdIds = addrToReadThreads.get(addr);
                    if (readThrdIds != null && Sets.union(wrtThrdIds, readThrdIds).size() > 1) {
                        sharedMemAddr.add(addr);
                    }
                }
            }
        }

        List<Event> reducedEvents = new ArrayList<>();
        for (int i = 0; i < numOfEvents; i++) {
            Event event = events[i];
            if (event.isReadOrWrite()) {
                if (sharedMemAddr.contains(event.getAddr())) {
                    reducedEvents.add(event);
                }
            } else if (event.getType().isLockType() || event.getType().isUnlockType()) {
                if (outermostLockingEvents.contains(event)) {
                    reducedEvents.add(event);
                }
            } else if (!event.isMetaEvent()) {
                reducedEvents.add(event);
            }
        }

        /* remove complete yet empty lock regions */
        Table<Long, Long, Event> lockEventTbl = HashBasedTable.create();
        Set<Event> criticalLockingEvents = new HashSet<>();
        for (Event event : reducedEvents) {
            long tid = event.getTID();
            Map<Long, Event> lockStatus = lockEventTbl.row(tid);

            if (event.acqLock()) {
                long lockId = event.getSyncObject();
                Event prevLock = lockStatus.put(lockId, event);
                assert prevLock == null : "Unexpected unmatched lock event: " + prevLock;
            } else if (event.relLock()) {
                long lockId = event.getSyncObject();
                Event lock = lockStatus.remove(lockId);
                if (lock == null || criticalLockingEvents.contains(lock)) {
                    criticalLockingEvents.add(event);
                }
            } else {
                for (Event e : lockStatus.values()) {
                    criticalLockingEvents.add(e);
                }
            }
        }
        criticalLockingEvents.addAll(lockEventTbl.values());
        for (Event event : reducedEvents) {
            if ((event.acqLock() || event.relLock())
                    && !criticalLockingEvents.contains(event)) {
                continue;
            }
            addEvent(event);
        }
    }

    static <K,V> List<V> getOrInitEmptyList(Map<K, List<V>> map, K key) {
        List<V> value = map.get(key);
        if (value == null) {
            value = Lists.newArrayList();
        }
        map.put(key, value);
        return value;
    }

    private static <K,V> Set<V> getOrInitEmptySet(Map<K, Set<V>> map, K key) {
        Set<V> value = map.get(key);
        if (value == null) {
            value = Sets.newHashSet();
        }
        map.put(key, value);
        return value;
    }

    private static class ThreadStatus {

        private final List<Integer> stacktrace;
        private final Map<Long, List<Event>> lockStatus;

        private ThreadStatus(List<Integer> stacktrace, Map<Long, List<Event>> lockStatus) {
            this.stacktrace = stacktrace;
            this.lockStatus = lockStatus;
        }
    }

}
