package com.runtimeverification.rvpredict.trace;

import com.runtimeverification.rvpredict.config.Configuration;
import com.runtimeverification.rvpredict.log.EventItem;
import com.runtimeverification.rvpredict.metadata.Metadata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Traian on 23.04.2015.
 */
public class LLVMTraceCache extends TraceCache {
    private BufferedReader traceFile = null;
    private final Metadata metadata;
    private static AtomicLong globalId = new AtomicLong(0);
    public LLVMTraceCache(Configuration config, Metadata metadata) {
        super(config, metadata);
        this.metadata = metadata;
    }

    @Override
    protected EventItem getNextEvent() throws IOException {
        if (traceFile == null) {
            traceFile = new BufferedReader(new FileReader(config.getLogDir()));
        }
        String line;
        do {
            line = traceFile.readLine();
        } while (line != null && (line.contains("<null>") || !line.startsWith("<gid")));
        if (line == null) {
            return null;
        }
        String[] parts = line.substring(line.indexOf('<') + 1, line.lastIndexOf('>')).split(";");
        assert parts.length == 10;
        long gid = parseLong("gid", parts[0]);
        long tid = parseLong("tid", parts[1]);
        long id = parseLong("id", parts[2]);
        long addrl = parseLong("addrl", parts[3]);
        long addrr = parseLong("addrr", parts[4]);
        long value = parseLong("value", parts[5]);
        EventType type = parseType("type", parts[6]);
        String fn = parseString("fn", parts[7]);
        String file = parseString("file", parts[8]);
        int ln = (int) parseLong("line", parts[9]);
        System.out.printf("<gid:%d;tid:%d;id:%d;addrl:%d;addrr:%d;value:%d;type:%s;fn:%s;file:%s;line:%d>%n",
                gid, tid, id, addrl, addrr, value, type.toString(), fn, file, ln);
        gid = globalId.incrementAndGet();
        id = metadata.getLocationId(String.format("<id:%d;fn:%s;file:%s;line:%d>", id, fn, file, ln));
        return new EventItem(gid, tid, (int) id, (int) addrl, (int) addrr, value, type);

    }

    private EventType parseType(String attr, String part) {
        String[] parts = part.split(":");
        assert parts.length == 2;
        assert parts[0].equals(attr);
        return EventType.valueOf(parts[1]);
    }

    private String parseString(String attr, String part) {
        assert attr.equals(part.substring(0,part.indexOf(':')));
        return part.substring(part.indexOf(':') + 1);
    }

    private long parseLong(String attr, String part) {
        String[] parts = part.split(":");
        assert parts.length == 2;
        assert parts[0].equals(attr);
        return Long.valueOf(parts[1]);
    }


}
