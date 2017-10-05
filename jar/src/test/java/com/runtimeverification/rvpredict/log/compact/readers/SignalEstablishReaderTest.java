package com.runtimeverification.rvpredict.log.compact.readers;

import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;
import com.runtimeverification.rvpredict.log.compact.CompactEvent;
import com.runtimeverification.rvpredict.log.compact.CompactEventFactory;
import com.runtimeverification.rvpredict.log.compact.CompactEventReader;
import com.runtimeverification.rvpredict.log.compact.Context;
import com.runtimeverification.rvpredict.log.compact.InvalidTraceDataException;
import com.runtimeverification.rvpredict.log.compact.TraceHeader;
import com.runtimeverification.rvpredict.util.Constants;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SignalEstablishReaderTest {
    private static final long ADDRESS = 1234567890123456L;
    private static final int SIGNAL_NUMBER = 12345;
    private static final int SIGNAL_MASK_NUMBER = 6789;

    @Mock private CompactEvent mockCompactEvent;
    @Mock private Context mockContext;
    @Mock private TraceHeader mockTraceHeader;
    @Mock private CompactEventFactory mockCompactEventFactory;

    @Test
    public void computesDataSize_UsesDefaultDataSize4() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(4);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(4);

        CompactEventReader.Reader reader = SignalEstablishReader.createReader();
        Assert.assertEquals(12, ReaderUtils.firstPartSize(reader, mockTraceHeader));
    }

    @Test
    public void computesDataSize_UsesDefaultDataSize8() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(8);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(8);

        CompactEventReader.Reader reader = SignalEstablishReader.createReader();
        Assert.assertEquals(24, ReaderUtils.firstPartSize(reader, mockTraceHeader));
    }

    @Test
    public void computesDataSize_UsesAddressSize8() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(4);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(8);

        CompactEventReader.Reader reader = SignalEstablishReader.createReader();
        Assert.assertEquals(16, ReaderUtils.firstPartSize(reader, mockTraceHeader));
    }

    @Test
    public void readsData() throws InvalidTraceDataException {
        when(mockTraceHeader.getDefaultDataWidthInBytes()).thenReturn(4);
        when(mockTraceHeader.getPointerWidthInBytes()).thenReturn(8);
        when(mockCompactEventFactory.establishSignal(
                mockContext, Constants.INVALID_EVENT_ID, ADDRESS, SIGNAL_NUMBER, SIGNAL_MASK_NUMBER))
                .thenReturn(Collections.singletonList(mockCompactEvent));

        ByteBuffer buffer = ByteBuffer.allocate(24)
                .putLong(ADDRESS).putInt(SIGNAL_NUMBER).putInt(SIGNAL_MASK_NUMBER).putLong(Long.MAX_VALUE);
        buffer.rewind();

        CompactEventReader.Reader reader = SignalEstablishReader.createReader();
        List<ReadonlyEventInterface> events =
                reader.readEvent(
                        mockContext, Constants.INVALID_EVENT_ID, mockCompactEventFactory, mockTraceHeader, buffer);

        Assert.assertEquals(1, events.size());
        Assert.assertEquals(mockCompactEvent, events.get(0));
    }
}
