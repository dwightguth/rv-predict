package com.runtimeverification.rvpredict.log.compact.readers;

import com.runtimeverification.rvpredict.log.compact.CompactEvent;
import com.runtimeverification.rvpredict.log.compact.CompactEventReader;
import com.runtimeverification.rvpredict.log.compact.Context;
import com.runtimeverification.rvpredict.log.compact.InvalidTraceDataException;
import com.runtimeverification.rvpredict.log.compact.TraceHeader;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AtomicReadModifyWriteReaderTest {
    private static final long ADDRESS = 1234567890123456L;
    private static final int READ_VALUE = 1234;
    private static final int WRITE_VALUE = 5678;

    @Mock private CompactEvent mockCompactEvent;
    @Mock private Context mockContext;
    @Mock private TraceHeader mockTraceHeader;
    @Mock private CompactEventReader mockCompactEventReader;

    @Test
    public void computesTheCorrectSizeDataSize_UsesDefaultSizeWhenLarger() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(4);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(4);

        AtomicReadModifyWriteReader reader = new AtomicReadModifyWriteReader(2);
        Assert.assertEquals(12, reader.size(mockTraceHeader));
    }

    @Test
    public void computesTheCorrectSizeDataSize_DoesNotUseDefaultSizeWhenSmaller() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(1);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(4);

        AtomicReadModifyWriteReader reader = new AtomicReadModifyWriteReader(2);
        Assert.assertEquals(8, reader.size(mockTraceHeader));
    }

    @Test
    public void computesTheCorrectSizeDataSize_UsesPointerSizeForAddress() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(4);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(8);

        AtomicReadModifyWriteReader reader = new AtomicReadModifyWriteReader(2);
        Assert.assertEquals(16, reader.size(mockTraceHeader));
    }

    @Test
    public void readsData() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(4);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(8);
        when(mockCompactEventReader.atomicReadModifyWrite(mockContext, 2, ADDRESS, READ_VALUE, WRITE_VALUE))
                .thenReturn(Collections.singletonList(mockCompactEvent));

        ByteBuffer buffer = ByteBuffer.allocate(24)
                .putLong(ADDRESS).putInt(READ_VALUE).putInt(WRITE_VALUE)
                .putLong(Long.MAX_VALUE);
        buffer.rewind();

        AtomicReadModifyWriteReader reader = new AtomicReadModifyWriteReader(2);
        List<CompactEvent> events = reader.readEvent(mockContext, mockCompactEventReader, mockTraceHeader, buffer);

        Assert.assertEquals(1, events.size());
        Assert.assertEquals(mockCompactEvent, events.get(0));
    }
}
