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
package smt;

import rvpredict.trace.Event;
import rvpredict.trace.EventType;
import rvpredict.trace.MemoryAccessEvent;
import rvpredict.trace.SyncEvent;
import rvpredict.trace.LockRegion;
import rvpredict.trace.ReadEvent;
import rvpredict.trace.Trace;
import rvpredict.trace.WriteEvent;
import graph.LockSetEngine;
import graph.ReachabilityEngine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import rvpredict.config.Configuration;

public class SMTConstraintBuilder {

    private int id = 0;// constraint id
    private SMTTaskRun task;

    private final Configuration config;

    private final Trace trace;

    private final ReachabilityEngine reachEngine = new ReachabilityEngine();
    private final LockSetEngine lockEngine = new LockSetEngine();

    // constraints below
    private final StringBuilder smtlibDecl = new StringBuilder(":extrafuns (\n");
    private final StringBuilder smtlibAssertion = new StringBuilder(":formula (and \n");
    private static final String CONS_SETLOGIC = ":logic QF_IDL\n";

    private final String benchname;

    public SMTConstraintBuilder(Configuration config, Trace trace) {
        this.config = config;
        this.trace = trace;
        benchname = "(benchmark " + config.tableName + ".smt\n";
    }

    private static String makeOrderVariable(Event event) {
        return "o" + event.getGID();
    }

    /**
     * Declares an order variable for each event in a given trace.
     */
    public void declareVariables() {
        for (Event e : trace.getAllEvents()) {
            smtlibDecl.append(String.format("(%s Int)\n", makeOrderVariable(e)));
        }
        smtlibDecl.append(")\n");
    }

    private void assertHappensBefore(Event e1, Event e2) {
        smtlibAssertion.append(String.format("(< %s %s)\n", makeOrderVariable(e1),
                makeOrderVariable(e2)));
        reachEngine.addEdge(e1, e2);
    }

    private void assertMutualExclusion(LockRegion lockRegion1, LockRegion lockRegion2) {
        String case1 = String.format("(< %s %s)",
                lockRegion1.getUnlock() != null ? makeOrderVariable(lockRegion1.getUnlock())
                        : makeOrderVariable(trace.getLastThreadEvent(lockRegion1.getThreadId())),
                lockRegion2.getLock() != null ? makeOrderVariable(lockRegion2.getLock())
                        : makeOrderVariable(trace.getFirstThreadEvent(lockRegion2.getThreadId())));
        String case2 = String.format("(< %s %s)",
                lockRegion2.getUnlock() != null ? makeOrderVariable(lockRegion2.getUnlock())
                        : makeOrderVariable(trace.getLastThreadEvent(lockRegion2.getThreadId())),
                lockRegion1.getLock() != null ? makeOrderVariable(lockRegion1.getLock())
                        : makeOrderVariable(trace.getFirstThreadEvent(lockRegion1.getThreadId())));
        smtlibAssertion.append(String.format("(or %s %s)\n", case1, case2));
    }

    /**
     * Adds intra-thread must happens-before (MHB) constraints of sequential
     * consistent memory model for a given trace.
     */
    public void addIntraThreadConstraints() {
        for (List<Event> events : trace.getThreadIdToEventsMap().values()) {
            Event prevEvent = events.get(0);
            for (Event crntEvent : events.subList(1, events.size())) {
                assertHappensBefore(prevEvent, crntEvent);
                prevEvent = crntEvent;
            }
        }
    }

    /**
     * Adds intra-thread must happens-before (MHB) constraints of relaxed PSO
     * memory model for a given trace.
     */
    public void addPSOIntraThreadConstraints() {
        for (List<MemoryAccessEvent> nodes : trace.getMemAccessEventsTable().values()) {
            MemoryAccessEvent prevEvent = nodes.get(0);
            for (MemoryAccessEvent crntEvent : nodes.subList(1, nodes.size())) {
                assertHappensBefore(prevEvent, crntEvent);
                prevEvent = crntEvent;
            }
        }
    }

    /**
     * Adds must happens-before constraints (MHB) for a given trace.
     */
    public void addMHBConstraints() {
        for (List<SyncEvent> startOrJoinEvents : trace.getThreadIdToStartJoinEvents().values()) {
            for (SyncEvent startOrJoinEvent : startOrJoinEvents) {
                long threadId = startOrJoinEvent.getSyncObject();
                if (startOrJoinEvent.getType().equals(EventType.START)) {
                    Event fstThrdEvent = trace.getFirstThreadEvent(threadId);
                    /* YilongL: it's possible that the first event of the new
                     * thread is not in the current trace */
                    if (fstThrdEvent != null) {
                        assertHappensBefore(startOrJoinEvent, fstThrdEvent);
                    }
                } else if (startOrJoinEvent.getType().equals(EventType.JOIN)) {
                    Event lastThrdEvent = trace.getLastThreadEvent(threadId);
                    /* YilongL: it's possible that the last event of the thread
                     * to join is not in the current trace */
                    if (lastThrdEvent != null) {
                        assertHappensBefore(lastThrdEvent, startOrJoinEvent);
                    }
                } else {
                    assert false : "unexpected event: " + startOrJoinEvent;
                }
            }
        }
    }

    /**
     * Adds lock mutual exclusion constraints for a given trace.
     */
    public void addLockingConstraints() {
        /* enumerate the locking events on each intrinsic lock */
        for (List<SyncEvent> syncEvents : trace.getLockObjToSyncEvents().values()) {
            Map<Long, Deque<SyncEvent>> threadIdToLockStack = Maps.newHashMap();
            List<LockRegion> lockRegions = Lists.newArrayList();
            Deque<SyncEvent> waitQueue = new ArrayDeque<>();
            Deque<SyncEvent> notifyQueue = new ArrayDeque<>();

            for (SyncEvent syncEvent : syncEvents) {
                long tid = syncEvent.getTID();

                if (syncEvent.getType().equals(EventType.LOCK)) {
                    safeStackMapGet(threadIdToLockStack, tid).push(syncEvent);
                } else if (syncEvent.getType().equals(EventType.UNLOCK)) {
                    Deque<SyncEvent> lockStack = safeStackMapGet(threadIdToLockStack, tid);
                    if (lockStack.size() <= 1) {
                        SyncEvent lockEvent = lockStack.isEmpty() ? null : lockStack.pop();
                        lockRegions.add(new LockRegion(lockEvent, syncEvent));
                    } else {
                        /* discard reentrant lock region */
                        lockStack.pop();
                    }
                } else if (syncEvent.getType().equals(EventType.WAIT)) {
                    if (notifyQueue.isEmpty()) {
                        waitQueue.addLast(syncEvent);
                    } else {
                        matchWaitNotifyPair(syncEvent, notifyQueue.removeFirst());
                    }

                    /* model wait event as two consecutive unlock-lock events */
                    Deque<SyncEvent> stack = safeStackMapGet(threadIdToLockStack, tid);
                    /* unlock */
                    if (stack.size() <= 1) {
                        lockRegions.add(new LockRegion(stack.isEmpty() ? null : stack.pop(),
                                syncEvent));
                    } else {
                        /* discard reentrant lock region */
                        stack.pop();
                    }
                    /* lock */
                    stack.push(syncEvent);
                } else if (syncEvent.getType().equals(EventType.NOTIFY)) {
                    if (waitQueue.isEmpty()) {
                        notifyQueue.addLast(syncEvent);
                    } else {
                        matchWaitNotifyPair(waitQueue.removeFirst(), syncEvent);
                    }
                }
            }

            for (Deque<SyncEvent> lockStack : threadIdToLockStack.values()) {
                /* the corresponding unlock events are missing in the current trace window */
                if (lockStack.size() > 0) {
                    SyncEvent lockEvent = lockStack.peek();
                    lockRegions.add(new LockRegion(lockEvent, null));
                }
            }

            lockEngine.addAll(lockRegions);

            for (LockRegion lockRegion1 : lockRegions) {
                for (LockRegion lockRegion2 : lockRegions) {
                    if (lockRegion1.getThreadId() < lockRegion2.getThreadId()) {
                        assertMutualExclusion(lockRegion1, lockRegion2);
                    }
                }
            }
        }
    }

    /**
     * Matches a given pair of wait-notify events, i.e., enforces the notify
     * event to happen in between the equivalent unlock-lock pair of the wait
     * event.
     */
    private void matchWaitNotifyPair(SyncEvent waitEvent, SyncEvent notifyEvent) {
        Event nextThrdEvent = trace.getNextThreadEvent(waitEvent);
        assertHappensBefore(notifyEvent, nextThrdEvent == null ? waitEvent
                : nextThrdEvent);
    }

    /**
     * return the read-write constraints
     *
     * @param readNodes
     * @param indexedWriteNodes
     * @param initValueMap
     * @return
     */
    // TODO: NEED to handle the feasibility of new added write nodes
    public StringBuilder constructCausalReadWriteConstraints(long rgid,
            List<ReadEvent> readNodes, Trace trace) {
        StringBuilder CONS_CAUSAL_RW = new StringBuilder("");

        for (int i = 0; i < readNodes.size(); i++) {

            ReadEvent rnode = readNodes.get(i);
            // filter out itself --
            if (rgid == rnode.getGID())
                continue;

            // get all write nodes on the address
            List<WriteEvent> writenodes = trace.getWriteEventsOn(rnode.getAddr());
            // no write to array field?
            // Yes, it could be: java.io.PrintStream out
            if (writenodes == null || writenodes.size() < 1)//
                continue;

            WriteEvent preNode = null;//

            // get all write nodes on the address & write the same value
            List<WriteEvent> writenodes_value_match = new ArrayList<>();
            for (int j = 0; j < writenodes.size(); j++) {
                WriteEvent wnode = writenodes.get(j);
                if (wnode.getValue() == rnode.getValue() && !canReach(rnode, wnode)) {
                    if (wnode.getTID() != rnode.getTID())
                        writenodes_value_match.add(wnode);
                    else {
                        if (preNode == null
                                || (preNode.getGID() < wnode.getGID() && wnode.getGID() < rnode
                                        .getGID()))
                            preNode = wnode;

                    }
                }
            }
            if (writenodes_value_match.size() > 0) {
                if (preNode != null)
                    writenodes_value_match.add(preNode);

                // TODO: consider the case when preNode is not null

                String var_r = makeOrderVariable(rnode);

                String cons_a = "";
                String cons_a_end = "";

                String cons_b = "";
                String cons_b_end = "";

                // make sure all the nodes that x depends on read the same value

                for (int j = 0; j < writenodes_value_match.size(); j++) {
                    WriteEvent wnode1 = writenodes_value_match.get(j);
                    String var_w1 = makeOrderVariable(wnode1);

                    String cons_b_ = "(> " + var_r + " " + var_w1 + ")\n";

                    String cons_c = "";
                    String cons_c_end = "";
                    String last_cons_d = null;
                    for (int k = 0; k < writenodes.size(); k++) {
                        WriteEvent wnode2 = writenodes.get(k);
                        if (!writenodes_value_match.contains(wnode2) && !canReach(wnode2, wnode1)
                                && !canReach(rnode, wnode2)) {
                            String var_w2 = makeOrderVariable(wnode2);

                            if (last_cons_d != null) {
                                cons_c += "(and " + last_cons_d;
                                cons_c_end += ")";

                            }
                            last_cons_d = "(or (> " + var_w2 + " " + var_r + ")" + " (> " + var_w1
                                    + " " + var_w2 + "))\n";

                        }
                    }
                    if (last_cons_d != null) {
                        cons_c += last_cons_d;
                    }
                    cons_c = cons_c + cons_c_end;

                    if (cons_c.length() > 0)
                        cons_b_ = "(and " + cons_b_ + " " + cons_c + ")\n";

                    if (j + 1 < writenodes_value_match.size()) {
                        cons_b += "(or " + cons_b_;
                        cons_b_end += ")";

                        cons_a += "(and (> " + var_w1 + " " + var_r + ")\n";
                        cons_a_end += ")";
                    } else {
                        cons_b += cons_b_;
                        cons_a += "(> " + var_w1 + " " + var_r + ")\n";
                    }
                }

                cons_b += cons_b_end;

                Long rValue = rnode.getValue();
                Long initValue = trace.getInitValueOf(rnode.getAddr());

                // it's possible that we don't have initial value for static
                // variable
                // so we allow init value to be zero or null? -- null is turned
                // into 0 by System.identityHashCode
                boolean allowMatchInit = true;
                if (initValue == null) {
                    // it's possible for the read node to match with the init
                    // write node
                    // if preNode is null
                    if (preNode != null) {
                        allowMatchInit = false;
                    }
                }

                if (initValue == null && allowMatchInit || initValue != null
                        && rValue.equals(initValue)) {
                    if (cons_a.length() > 0) {
                        cons_a += cons_a_end + "\n";
                        CONS_CAUSAL_RW.append("(or \n" + cons_a + " " + cons_b + ")\n\n");
                    }
                } else {
                    CONS_CAUSAL_RW.append(cons_b + "\n");
                }
            } else {
                // make sure it reads the initial write
                Long rValue = rnode.getValue();
                Long initValue = trace.getInitValueOf(rnode.getAddr());

                if (initValue != null && rValue.equals(initValue)) {
                    String var_r = makeOrderVariable(rnode);

                    for (int k = 0; k < writenodes.size(); k++) {
                        WriteEvent wnode3 = writenodes.get(k);
                        if (wnode3.getTID() != rnode.getTID() && !canReach(rnode, wnode3)) {
                            String var_w3 = makeOrderVariable(wnode3);

                            String cons_e = "(> " + var_w3 + " " + var_r + ")\n";
                            CONS_CAUSAL_RW.append(cons_e);
                        }

                    }

                }

            }

        }

        return CONS_CAUSAL_RW;
    }

    /**
     * Checks if two {@code MemoryAccessEvent} hold a common lock.
     */
    public boolean hasCommonLock(MemoryAccessEvent e1, MemoryAccessEvent e2) {
        return lockEngine.hasCommonLock(e1, e2);
    }

    /**
     * return true if node1 can reach node2 from the ordering relation
     *
     * @param node1
     * @param node2
     * @return
     */
    public boolean canReach(Event node1, Event node2) {
        long gid1 = node1.getGID();
        long gid2 = node2.getGID();

        return reachEngine.canReach(gid1, gid2);

    }

    /**
     * return true if the solver return a solution to the constraints
     *
     * @param e1
     * @param e2
     * @param casualConstraint
     * @return
     */
    public boolean isRace(Event e1, Event e2, StringBuilder casualConstraint) {
        String var1 = makeOrderVariable(e1);
        String var2 = makeOrderVariable(e2);

        // String QUERY = "\n(assert (= "+var1+" "+var2+"))\n\n";

        id++;
        task = new SMTTaskRun(config, id);

        String cons_assert = smtlibAssertion.toString() + casualConstraint.toString() + ")\n";
        cons_assert = cons_assert.replace(var2 + " ", var1 + " ");
        cons_assert = cons_assert.replace(var2 + ")", var1 + ")");
        StringBuilder msg = new StringBuilder(benchname).append(CONS_SETLOGIC)
                .append(smtlibDecl).append(cons_assert).append(")");
        task.sendMessage(msg.toString());

        return task.sat;
    }

    private static <K, E> Deque<E> safeStackMapGet(Map<K, Deque<E>> map, K key) {
        Deque<E> value = map.get(key);
        if (value == null) {
            value = new ArrayDeque<>();
            map.put(key, value);
        }
        return value;
    }

}
