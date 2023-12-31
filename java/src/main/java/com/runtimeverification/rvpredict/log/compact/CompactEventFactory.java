package com.runtimeverification.rvpredict.log.compact;

import com.runtimeverification.rvpredict.log.EventType;
import com.runtimeverification.rvpredict.log.LockRepresentation;
import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

public class CompactEventFactory {
    private static final List<ReadonlyEventInterface> NO_EVENTS = Collections.emptyList();

    private enum LockReason {
        NORMAL,
        ATOMIC,
        SIGNAL
    }

    // TODO(virgil): Make the Context a private local variable here, I don't think anyone else needs it.

    public List<ReadonlyEventInterface> dataManipulation(
            Context context,
            long originalEventId,
            CompactEventReader.DataManipulationType dataManipulationType,
            int dataSizeInBytes,
            long address,
            long value,
            CompactEventReader.Atomicity atomicity) throws InvalidTraceDataException {
        EventType compactType;
        switch (dataManipulationType) {
            case LOAD:
                compactType = EventType.READ;
                break;
            case STORE:
                compactType = EventType.WRITE;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown data manipulation type: " + dataManipulationType);
        }
        long addressId = context.createUniqueDataAddressId(address);
        if (atomicity == CompactEventReader.Atomicity.NOT_ATOMIC) {
            return Collections.singletonList(
                    dataManipulationEvent(
                            context, originalEventId, dataSizeInBytes, address, addressId, value, compactType));
        }
        // TODO(virgil): These locks should be something more fine-grained, e.g. write_locks.
        // Also, it would probably be nice if an atomic write to a variable would also be atomic for
        // access to a part of the variable (i.e. union in c/c++), if that's indeed how it should
        // work.
        return Arrays.asList(
                lockManipulationEvent(
                        context, originalEventId,
                        CompactEventReader.LockManipulationType.LOCK, address, LockReason.ATOMIC),
                dataManipulationEvent(
                        context, originalEventId, dataSizeInBytes, address, addressId, value, compactType),
                lockManipulationEvent(
                        context, originalEventId,
                        CompactEventReader.LockManipulationType.UNLOCK, address, LockReason.ATOMIC)
        );
    }

    private ReadonlyEventInterface dataManipulationEvent(
            Context context, long originalEventId, int dataSizeInBytes, long dataAddress, long dataAddressId,
            long value, EventType compactType) {
        return new CompactEvent(context, compactType, originalEventId) {
            @Override
            int getDataSizeInBytes() {
                return dataSizeInBytes;
            }

            @Override
            public long getDataInternalIdentifier() {
                return dataAddressId;
            }

            @Override
            public long unsafeGetDataInternalIdentifier() {
                return getDataInternalIdentifier();
            }

            @Override
            public long getDataObjectExternalIdentifier() {
                return dataAddress;
            }

            @Override
            public int getFieldIdOrArrayIndex() {
                return 0;
            }

            @Override
            public long getDataValue() {
                return value;
            }

            @Override
            public long unsafeGetDataValue() {
                return getDataValue();
            }

            @Override
            public String toString() {
                return super.toString() + "/" + dataAddressId + "(" + Long.toHexString(dataAddress) + ")=" + value;
            }
        };
    }

    public List<ReadonlyEventInterface> atomicReadModifyWrite(
            Context context,
            long originalEventId,
            int dataSizeInBytes,
            long dataAddress, long readValue, long writeValue) throws InvalidTraceDataException {
        long addressId = context.createUniqueDataAddressId(dataAddress);
        return Arrays.asList(
                lockManipulationEvent(
                        context, originalEventId,
                        CompactEventReader.LockManipulationType.LOCK, dataAddress, LockReason.ATOMIC),
                dataManipulationEvent(
                        context, originalEventId, dataSizeInBytes, dataAddress, addressId, readValue, EventType.READ),
                dataManipulationEvent(
                        context, originalEventId, dataSizeInBytes, dataAddress, addressId, writeValue, EventType.WRITE),
                lockManipulationEvent(
                        context, originalEventId,
                        CompactEventReader.LockManipulationType.UNLOCK, dataAddress, LockReason.ATOMIC));
    }

    public List<ReadonlyEventInterface> changeOfGeneration(Context context, long generation) {
        context.changeOfGeneration(generation);
        return NO_EVENTS;
    }

    public List<ReadonlyEventInterface> lockManipulation(
            Context context, long originalEventId,
            CompactEventReader.LockManipulationType lockManipulationType, long address)
            throws InvalidTraceDataException {
        return Collections.singletonList(
                lockManipulationEvent(context, originalEventId, lockManipulationType, address, LockReason.NORMAL));
    }

    private ReadonlyEventInterface lockManipulationEvent(
            Context context,
            long originalEventId,
            CompactEventReader.LockManipulationType lockManipulationType,
            long address,
            LockReason lockReason)
            throws InvalidTraceDataException {
        EventType compactType;
        switch (lockManipulationType) {
            case LOCK:
                compactType = EventType.WRITE_LOCK;
                break;
            case UNLOCK:
                compactType = EventType.WRITE_UNLOCK;
                break;
            default:
                throw new InvalidTraceDataException("Unknown lock manipulation type: " + lockManipulationType);
        }
        return new CompactEvent(context, compactType, originalEventId) {
            @Override
            public long getSyncObject() {
                return address;
            }

            @Override
            public LockRepresentation getLockRepresentation() {
                LockRepresentation.LockType lockType;
                switch (lockReason) {
                    case NORMAL:
                        lockType = LockRepresentation.LockType.WRITE_LOCK;
                        break;
                    case ATOMIC:
                        lockType = LockRepresentation.LockType.ATOMIC_LOCK;
                        break;
                    case SIGNAL:
                        lockType = LockRepresentation.LockType.SIGNAL_LOCK;
                        break;
                    default:
                        throw new IllegalStateException("Unknown lock type: " + lockReason);
                }
                return new LockRepresentation(lockType, getLockId());
            }

            @Override
            public boolean isAtomic() {
                return lockReason == LockReason.ATOMIC || lockReason == LockReason.SIGNAL;
            }

            @Override
            public String toString() {
                return super.toString() + "/" + getLockRepresentation();
            }
        };
    }

    List<ReadonlyEventInterface> jump(Context context, long address) throws InvalidTraceDataException {
        context.jump(address);
        return NO_EVENTS;
    }

    // Signal events.

    public List<ReadonlyEventInterface> establishSignal(
            Context context, long originalEventId,
            long handler, long signalNumber, long signalMaskNumber) throws InvalidTraceDataException {
        context.establishSignal(handler, signalNumber, signalMaskNumber);
        long signalMask = context.getMemoizedSignalMask(signalMaskNumber);
        long addressId = context.createUniqueSignalHandlerId(signalNumber);
        return Arrays.asList(
                lockManipulationEvent(
                        context, originalEventId, CompactEventReader.LockManipulationType.LOCK,
                        com.runtimeverification.rvpredict.util.Constants.SIGNAL_LOCK_C, LockReason.SIGNAL),
                new CompactEvent(context, EventType.ESTABLISH_SIGNAL, originalEventId) {
                    @Override
                    public long getFullWriteSignalMask() {
                        return signalMask;
                    }

                    @Override
                    public long getSignalNumber() {
                        return signalNumber;
                    }

                    @Override
                    public long getSignalHandlerAddress() {
                        return handler;
                    }

                    @Override
                    public String toString() {
                        return super.toString() + "/" + signalNumber + "=@" + handler + " |=" + signalMask;
                    }
                },
                dataManipulationEvent(
                        context, originalEventId, Constants.LONG_SIZE_IN_BYTES,
                        signalNumber, addressId, handler, EventType.WRITE),
                lockManipulationEvent(
                        context, originalEventId, CompactEventReader.LockManipulationType.UNLOCK,
                        com.runtimeverification.rvpredict.util.Constants.SIGNAL_LOCK_C, LockReason.SIGNAL)
        );
    }

    public List<ReadonlyEventInterface> disestablishSignal(
            Context context, long originalEventId, long signalNumber) throws InvalidTraceDataException {
        context.disestablishSignal(signalNumber);
        long addressId = context.createUniqueSignalHandlerId(signalNumber);
        return Arrays.asList(
                lockManipulationEvent(
                        context, originalEventId, CompactEventReader.LockManipulationType.LOCK,
                        com.runtimeverification.rvpredict.util.Constants.SIGNAL_LOCK_C, LockReason.SIGNAL),
                new CompactEvent(context, EventType.DISESTABLISH_SIGNAL, originalEventId) {
                    public long getSignalNumber() {
                        return signalNumber;
                    }

                    @Override
                    public String toString() {
                        return super.toString() + "/" + signalNumber;
                    }
                },
                dataManipulationEvent(
                        context, originalEventId, Constants.LONG_SIZE_IN_BYTES,
                        signalNumber, addressId, Constants.INVALID_PROGRAM_COUNTER, EventType.WRITE),
                lockManipulationEvent(
                        context, originalEventId, CompactEventReader.LockManipulationType.UNLOCK,
                        com.runtimeverification.rvpredict.util.Constants.SIGNAL_LOCK_C, LockReason.SIGNAL)
        );
    }

    public List<ReadonlyEventInterface> enterSignal(
            Context context, long originalEventId,
            long generation, long signalNumber, long signalHandler) throws InvalidTraceDataException {
        context.enterSignal(signalNumber, generation);
        long addressId = context.createUniqueSignalHandlerId(signalNumber);
        return Arrays.asList(
                lockManipulationEvent(
                        context, originalEventId, CompactEventReader.LockManipulationType.LOCK,
                        com.runtimeverification.rvpredict.util.Constants.SIGNAL_LOCK_C, LockReason.SIGNAL),
                new CompactEvent(context, EventType.ENTER_SIGNAL, originalEventId) {
                    @Override
                    public long getSignalNumber() {
                        return signalNumber;
                    }

                    @Override
                    public long getSignalHandlerAddress() {
                        return signalHandler;
                    }

                    @Override
                    public String toString() {
                        return super.toString() + "/" + signalNumber;
                    }
                },
                dataManipulationEvent(
                        context, originalEventId, Constants.LONG_SIZE_IN_BYTES,
                        signalNumber, addressId, signalHandler, EventType.READ),
                lockManipulationEvent(
                        context, originalEventId, CompactEventReader.LockManipulationType.UNLOCK,
                        com.runtimeverification.rvpredict.util.Constants.SIGNAL_LOCK_C, LockReason.SIGNAL)
        );
    }

    public List<ReadonlyEventInterface> exitSignal(
            Context context, long originalEventId) throws InvalidTraceDataException {
        long currentSignal = context.getSignalNumber();
        context.exitSignal();
        return Collections.singletonList(new CompactEvent(context, EventType.EXIT_SIGNAL, originalEventId) {
            @Override
            public long getSignalNumber() {
                return currentSignal;
            }

            @Override
            public String toString() {
                return super.toString() + "/" + currentSignal;
            }
        });
    }

    public List<ReadonlyEventInterface> signalDepth(Context context, int signalDepth)
            throws InvalidTraceDataException {
        context.setSignalDepth(signalDepth);
        return NO_EVENTS;
    }

    public List<ReadonlyEventInterface> signalMaskMemoization(
            Context context, long signalMask, long originBitCount, long signalMaskNumber) {
        context.memoizeSignalMask(signalMask, originBitCount, signalMaskNumber);
        return NO_EVENTS;
    }

    public List<ReadonlyEventInterface> signalMask(Context context, long originalEventId, long signalMaskNumber) {
        long signalMask = context.getMemoizedSignalMask(signalMaskNumber);
        return Collections.singletonList(new CompactEvent(context, EventType.WRITE_SIGNAL_MASK, originalEventId) {
            @Override
            public long getFullWriteSignalMask() {
                return signalMask;
            }

            @Override
            public String toString() {
                return super.toString() + "/" + signalMask;
            }
        });
    }

    public List<ReadonlyEventInterface> blockSignals(Context context, long originalEventId, long signalMaskNumber) {
        long signalMask = context.getMemoizedSignalMask(signalMaskNumber);
        return Collections.singletonList(
                new CompactEvent(context, EventType.BLOCK_SIGNALS, originalEventId) {
                    @Override
                    public long getPartialSignalMask() {
                        return signalMask;
                    }

                    @Override
                    public String toString() {
                        return super.toString() + "/" + signalMask;
                    }
                });
    }

    public List<ReadonlyEventInterface> unblockSignals(Context context, long originalEventId, long signalMaskNumber) {
        long signalMask = context.getMemoizedSignalMask(signalMaskNumber);
        return Collections.singletonList(
                new CompactEvent(context, EventType.UNBLOCK_SIGNALS, originalEventId) {
                    @Override
                    public long getPartialSignalMask() {
                        return signalMask;
                    }

                    @Override
                    public String toString() {
                        return super.toString() + "/" + signalMask;
                    }
                });
    }

    public List<ReadonlyEventInterface> getSetSignalMask(
            Context context, long originalEventId, long readSignalMaskNumber, long writeSignalMaskNumber) {
        long readSignalMask = context.getMemoizedSignalMask(readSignalMaskNumber);
        long writeSignalMask = context.getMemoizedSignalMask(writeSignalMaskNumber);
        return Collections.singletonList(new CompactEvent(context, EventType.READ_WRITE_SIGNAL_MASK, originalEventId) {
            @Override
            public long getFullReadSignalMask() {
                return readSignalMask;
            }

            @Override
            public long getFullWriteSignalMask() {
                return writeSignalMask;
            }

            @Override
            public String toString() {
                return super.toString() + "/" + readSignalMask + "/" + writeSignalMask;
            }
        });
    }

    public List<ReadonlyEventInterface> getSignalMask(Context context, long originalEventId, long signalMaskNumber) {
        long signalMask = context.getMemoizedSignalMask(signalMaskNumber);
        return Collections.singletonList(new CompactEvent(context, EventType.READ_SIGNAL_MASK, originalEventId) {
            @Override
            public long getFullReadSignalMask() {
                return signalMask;
            }

            @Override
            public String toString() {
                return super.toString() + "/" + signalMask;
            }
        });
    }

    // Function events.

    public List<ReadonlyEventInterface> enterFunction(
            Context context, long originalEventId, long canonicalFrameAddress, OptionalLong callSiteAddress) {
        return Collections.singletonList(new CompactEvent(context, EventType.INVOKE_METHOD, originalEventId) {
            @Override
            public long getCanonicalFrameAddress() {
                return canonicalFrameAddress;
            }

            @Override
            public OptionalLong getCallSiteAddress() {
                return callSiteAddress;
            }

            @Override
            public String toString() {
                return super.toString() + String.format("/(cfa=%016x)", canonicalFrameAddress);
            }
        });
    }

    public List<ReadonlyEventInterface> exitFunction(Context context, long originalEventId) {
        return Collections.singletonList(new CompactEvent(context, EventType.FINISH_METHOD, originalEventId) {
        });
    }

    // Thread events.

    public List<ReadonlyEventInterface> beginThread(
            Context context, long originalEventId, long threadId, long generation)
            throws InvalidTraceDataException {
        context.beginThread(threadId, generation);
        return Collections.singletonList(new CompactEvent(context, EventType.BEGIN_THREAD, originalEventId) {
        });
    }

    List<ReadonlyEventInterface> endThread(Context context, long originalEventId) {
        context.endThread();
        return Collections.singletonList(new CompactEvent(context, EventType.END_THREAD, originalEventId) {
        });
    }

    public List<ReadonlyEventInterface> threadSync(
            Context context, long originalEventId, CompactEventReader.ThreadSyncType threadSyncType, long threadId)
            throws InvalidTraceDataException {
        EventType compactType;
        switch (threadSyncType) {
            case JOIN:
                context.joinThread(threadId);
                compactType = EventType.JOIN_THREAD;
                break;
            case FORK:
                context.startThread(threadId);
                compactType = EventType.START_THREAD;
                break;
            case SWITCH:
                context.switchThread(threadId);
                return NO_EVENTS;
            default:
                throw new InvalidTraceDataException("Unknown thread sync event: " + threadSyncType);
        }
        return Collections.singletonList(new CompactEvent(context, compactType, originalEventId) {
            @Override
            public long getSyncedThreadId() {
                return threadId;
            }
        });
    }
}
