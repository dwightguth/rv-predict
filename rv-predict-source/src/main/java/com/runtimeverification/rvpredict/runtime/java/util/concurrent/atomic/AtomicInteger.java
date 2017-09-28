/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package com.runtimeverification.rvpredict.runtime.java.util.concurrent.atomic;
import java.util.function.IntUnaryOperator;
import java.util.function.IntBinaryOperator;

import com.runtimeverification.rvpredict.log.EventType;
import com.runtimeverification.rvpredict.runtime.RVPredictRuntime;

import sun.misc.Unsafe;

/**
 * An {@code int} value that may be updated atomically.  See the
 * {@link java.util.concurrent.atomic} package specification for
 * description of the properties of atomic variables. An
 * {@code AtomicInteger} is used in applications such as atomically
 * incremented counters, and cannot be used as a replacement for an
 * {@link java.lang.Integer}. However, this class does extend
 * {@code Number} to allow uniform access by tools and utilities that
 * deal with numerically-based classes.
 *
 * @since 1.5
 * @author Doug Lea
*/
public class AtomicInteger extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 6214790243416807050L;

    private static final int RVPREDICT_ATOMIC_INTEGER_LOC_ID = RVPredictRuntime.metadata
            .getLocationId("java.util.concurrent.atomic.AtomicInteger(AtomicInteger.java:n/a)");
    private static final int RVPREDICT_ATOMIC_INTEGER_VALUE_ID = RVPredictRuntime.metadata
            .getVariableId("java.util.concurrent.atomic.AtomicInteger", "value");

    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private volatile int value;

    // RV-Predict logging methods

    /**
     * Ensures that when a read event reads the value set by an atomic
     * "read-and-write" operation, that read event is logged after the write
     * event generated by that atomic operation.
     */
    private final Object _rvpredict_sync = new Object();

    private void _rvpredict_atomic_read(int value) {
        RVPredictRuntime.saveAtomicEvent(EventType.ATOMIC_READ, RVPREDICT_ATOMIC_INTEGER_LOC_ID,
                System.identityHashCode(this), -RVPREDICT_ATOMIC_INTEGER_VALUE_ID, value, 0);
    }

    private void _rvpredict_atomic_write(int value) {
        RVPredictRuntime.saveAtomicEvent(EventType.ATOMIC_WRITE, RVPREDICT_ATOMIC_INTEGER_LOC_ID,
                System.identityHashCode(this), -RVPREDICT_ATOMIC_INTEGER_VALUE_ID, value, 0);
    }

    private void _rvpredict_atomic_read_then_write(int oldValue, int newValue) {
        RVPredictRuntime.saveAtomicEvent(EventType.ATOMIC_READ_THEN_WRITE, RVPREDICT_ATOMIC_INTEGER_LOC_ID,
                System.identityHashCode(this), -RVPREDICT_ATOMIC_INTEGER_VALUE_ID, oldValue, newValue);
    }

    private int _rvpredict_get_value() {
        synchronized (_rvpredict_sync) {
            int value = this.value;
            _rvpredict_atomic_read(value);
            return value;
        }
    }

    private void _rvpredict_set_value(int newValue) {
        _rvpredict_atomic_write(newValue);
        value = newValue;
    }

    private boolean _rvpredict_compare_and_set_value(int expect, int update) {
        for (;;) {
            synchronized (_rvpredict_sync) {
                if (unsafe.compareAndSwapInt(this, valueOffset, expect, update)) {
                    _rvpredict_atomic_read_then_write(expect, update);
                    return true;
                }
            }

            int actual = value;
            if (expect != actual) {
                _rvpredict_atomic_read(actual);
                return false;
            } else {
                // if "actual == expect", it would be unsound to log an
                // ATOMIC_READ event that reads `expect' and return false
                // because when we match this read with some write of the same
                // value this CAS should really succeed and return true
            }
        }
    }

    private int _rvpredict_get_and_set_value(int newValue) {
        synchronized (_rvpredict_sync) {
            int oldValue = unsafe.getAndSetInt(this, valueOffset, newValue);
            _rvpredict_atomic_read_then_write(oldValue, newValue);
            return oldValue;
        }
    }

    private int _rvpredict_get_and_add_value(int delta) {
        synchronized (_rvpredict_sync) {
            int oldValue = unsafe.getAndAddInt(this, valueOffset, delta);
            _rvpredict_atomic_read_then_write(oldValue, oldValue + delta);
            return oldValue;
        }
    }

    /**
     * Creates a new AtomicInteger with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicInteger(int initialValue) {
        _rvpredict_set_value(initialValue);
    }

    /**
     * Creates a new AtomicInteger with initial value {@code 0}.
     */
    public AtomicInteger() {
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final int get() {
        return _rvpredict_get_value();
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(int newValue) {
        _rvpredict_set_value(newValue);
    }

    /**
     * Eventually sets to the given value.
     *
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(int newValue) {
        _rvpredict_set_value(newValue);
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final int getAndSet(int newValue) {
        return _rvpredict_get_and_set_value(newValue);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(int expect, int update) {
        return _rvpredict_compare_and_set_value(expect, update);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * <p><a href="package-summary.html#weakCompareAndSet">May fail
     * spuriously and does not provide ordering guarantees</a>, so is
     * only rarely an appropriate alternative to {@code compareAndSet}.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful
     */
    public final boolean weakCompareAndSet(int expect, int update) {
        return _rvpredict_compare_and_set_value(expect, update);
    }

    /**
     * Atomically increments by one the current value.
     *
     * @return the previous value
     */
    public final int getAndIncrement() {
        return _rvpredict_get_and_add_value(1);
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @return the previous value
     */
    public final int getAndDecrement() {
        return _rvpredict_get_and_add_value(-1);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final int getAndAdd(int delta) {
        return _rvpredict_get_and_add_value(delta);
    }

    /**
     * Atomically increments by one the current value.
     *
     * @return the updated value
     */
    public final int incrementAndGet() {
        return _rvpredict_get_and_add_value(1) + 1;
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @return the updated value
     */
    public final int decrementAndGet() {
        return _rvpredict_get_and_add_value(-1) - 1;
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final int addAndGet(int delta) {
        return _rvpredict_get_and_add_value(delta) + delta;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the previous value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            prev = get();
            next = updateFunction.applyAsInt(prev);
        } while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final int updateAndGet(IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            prev = get();
            next = updateFunction.applyAsInt(prev);
        } while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the previous value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final int getAndAccumulate(int x,
                                      IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get();
            next = accumulatorFunction.applyAsInt(prev, x);
        } while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the updated value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final int accumulateAndGet(int x,
                                      IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get();
            next = accumulatorFunction.applyAsInt(prev, x);
        } while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * Returns the String representation of the current value.
     * @return the String representation of the current value
     */
    public String toString() {
        return Integer.toString(get());
    }

    /**
     * Returns the value of this {@code AtomicInteger} as an {@code int}.
     */
    public int intValue() {
        return get();
    }

    /**
     * Returns the value of this {@code AtomicInteger} as a {@code long}
     * after a widening primitive conversion.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public long longValue() {
        return get();
    }

    /**
     * Returns the value of this {@code AtomicInteger} as a {@code float}
     * after a widening primitive conversion.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public float floatValue() {
        return get();
    }

    /**
     * Returns the value of this {@code AtomicInteger} as a {@code double}
     * after a widening primitive conversion.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public double doubleValue() {
        return get();
    }

}