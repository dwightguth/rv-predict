package com.runtimeverification.rvpredict.log.compact;

import com.google.common.annotations.VisibleForTesting;
import com.runtimeverification.rvpredict.log.EventType;
import com.runtimeverification.rvpredict.log.LockRepresentation;
import com.runtimeverification.rvpredict.log.ReadonlyEvent;
import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;

import java.util.OptionalLong;

public abstract class CompactEvent extends ReadonlyEvent {
    private long eventId;
    private long locationId;
    private final long originalThreadId;
    private final int signalDepth;
    private final EventType type;
    private final long originalEventId;

    CompactEvent(Context context, EventType type, long originalEventId) {
        this(context.newId(), context.getPC(), context.getThreadId(), context.getSignalDepth(), type, originalEventId);
    }

    @VisibleForTesting
    public CompactEvent(
            long eventId,
            long locationId,
            long originalThreadId,
            int signalDepth,
            EventType type,
            long originalEventId) {
        this.eventId = eventId;
        this.locationId = locationId;
        this.originalThreadId = originalThreadId;
        this.signalDepth = signalDepth;
        this.originalEventId = originalEventId;
        this.type = type;
    }

    @Override
    public long getEventId() {
        return eventId;
    }

    @Override
    public long getLocationId() {
        return locationId;
    }

    @Override
    public long getOriginalThreadId() {
        return originalThreadId;
    }

    @Override
    public int getSignalDepth() {
        return signalDepth;
    }

    @Override
    public EventType getType() {return type;}

    int getDataSizeInBytes() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getDataInternalIdentifier() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getDataValue() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getSyncedThreadId() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getSyncObject() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getSignalNumber() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getPartialSignalMask() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }
    @Override
    public long getFullReadSignalMask() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }
    @Override
    public long getFullWriteSignalMask() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }
    @Override
    public long getSignalHandlerAddress() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public LockRepresentation getLockRepresentation() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getCanonicalFrameAddress() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long getDataObjectExternalIdentifier() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public int getFieldIdOrArrayIndex() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public boolean isAtomic() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public OptionalLong getCallSiteAddress() {
        throw new UnsupportedOperationException("Unsupported operation for " + getType());
    }

    @Override
    public long unsafeGetDataInternalIdentifier() {
        return 0;
    }

    @Override
    public long unsafeGetDataValue() {
        return 0;
    }

    @Override
    public ReadonlyEvent copy() {
        return this;
    }

    @Override
    public ReadonlyEventInterface destructiveWithLocationId(long locationId) {
        this.locationId = locationId;
        return this;
    }

    @Override
    public ReadonlyEventInterface destructiveWithEventId(long eventId) {
        this.eventId = eventId;
        return this;
    }

    @Override
    public long getOriginalId() {
        return originalEventId;
    }

    @Override
    public String toString() {
        return String.format(
                "%s(ID:%s OID:%s Loc:%s Otid:%s SD:%s)",
                type, eventId, originalEventId, locationId, originalThreadId, signalDepth);
    }
}
