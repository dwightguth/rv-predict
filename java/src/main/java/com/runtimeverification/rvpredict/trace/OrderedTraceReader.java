package com.runtimeverification.rvpredict.trace;

import com.runtimeverification.rvpredict.log.IEventReader;
import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;

import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Class for reading a trace from memory in the order given by event ids
 *
 * @author TraianSF
 */
public class OrderedTraceReader implements IEventReader {

    private Iterator<ReadonlyEventInterface> stream;
    private ReadonlyEventInterface lastRead;

    /**
     * Reinitializes the trace to be read
     * @param trace
     */
    public void reset(Trace trace) {
        stream = trace.eventsByThreadID().values().stream().flatMap(List::stream).sorted().iterator();
    }

    @Override
    public ReadonlyEventInterface readEvent() throws IOException {
        if (stream != null && stream.hasNext()) {
            lastRead = stream.next();
        } else {
            throw new EOFException();
        }
        return lastRead;
    }

    @Override
    public ReadonlyEventInterface lastReadEvent() {
        return lastRead;
    }

    @Override
    public void close() throws IOException {

    }
}
