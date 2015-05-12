package com.runtimeverification.rvpredict.metadata;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;

import com.runtimeverification.rvpredict.trace.MemoryAddr;

@SuppressWarnings("serial")
public class Metadata implements Serializable {

    public static final int MAX_NUM_OF_VARIABLES = 1024 * 128;

    // this should be enough for more than 1 million lines of code
    public static final int MAX_NUM_OF_LOCATIONS = 1024 * 1024;

    private transient final AtomicInteger nextVarId = new AtomicInteger(1);

    private transient final AtomicInteger nextLocId = new AtomicInteger(1);

    private transient final ConcurrentHashMap<String, Integer> varSigToVarId = new ConcurrentHashMap<>();

    private transient final ConcurrentHashMap<String, Integer> locSigToLocId = new ConcurrentHashMap<>();

    private final String[] varIdToVarSig = new String[MAX_NUM_OF_VARIABLES];

    private final String[] locIdToLocSig = new String[MAX_NUM_OF_LOCATIONS];

    private final Set<Integer> volatileVarIds = Collections
            .newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private final Map<Long, Pair<Long, Integer>> tidToCreationInfo = new ConcurrentHashMap<>();

    private static final Metadata instance = new Metadata();

    /**
     * Note: This method should be used only in a few places and definitely NOT
     * in offline prediction which should get its {@code Metadata} instance from
     * {@link #readFrom(Path)}.
     *
     * @return a singleton instance of {@code Metadata}.
     */
    public static Metadata singleton() {
        return instance;
    }

    private Metadata() { }

    public int getVariableId(String cname, String fname) {
        String varSig = cname + "." + fname;
        Integer varId = varSigToVarId.get(varSig);
        if (varId == null) {
            varSigToVarId.putIfAbsent(varSig, nextVarId.getAndIncrement());
            varId = varSigToVarId.get(varSig);
            varIdToVarSig[varId] = varSig;
        }
        return varId;
    }

    public String getVariableSig(int varId) {
        return varIdToVarSig[varId];
    }

    public int getLocationId(String locSig) {
        Integer locId = locSigToLocId.get(locSig);
        if (locId == null) {
            locSigToLocId.putIfAbsent(locSig, nextLocId.getAndIncrement());
            locId = locSigToLocId.get(locSig);
            locIdToLocSig[locId] = locSig;
        }
        return locId;
    }

    public String getLocationSig(int locId) {
        return locIdToLocSig[locId];
    }

    public void addVolatileVariable(String cname, String fname) {
        volatileVarIds.add(getVariableId(cname, fname));
    }

    /**
     * Checks if a memory address is volatile.
     *
     * @param addr
     *            the memory address
     * @return {@code true} if the address is {@code volatile}; otherwise,
     *         {@code false}
     */
    public boolean isVolatile(MemoryAddr addr) {
        int varId = addr.fieldIdOrArrayIndex();
        return varId < 0 && volatileVarIds.contains(-varId);
    }

    public void addThreadCreationInfo(long childTID, long parentTID, int locId) {
        tidToCreationInfo.put(childTID, Pair.of(parentTID, locId));
    }

    public long getParentTID(long tid) {
        Pair<Long, Integer> info = tidToCreationInfo.get(tid);
        return info == null ? 0 : info.getLeft();
    }

    public int getThreadCreationLocId(long tid) {
        Pair<Long, Integer> info = tidToCreationInfo.get(tid);
        return info == null ? -1 : info.getRight();
    }

    /**
     * Deserializes the {@code Metadata} object stored at the specified location.
     * <p>
     * This method should only be used in offline prediction to obtain an
     * instance of {@code Metadata}.
     *
     * @param path
     *            the location where the metadata is stored
     * @return the {@code Metadata} object
     */
    public static Metadata readFrom(Path path) {
        try (ObjectInputStream metadataIS = new ObjectInputStream(new BufferedInputStream(
                new FileInputStream(path.toFile())))) {
            return (Metadata) metadataIS.readObject();
        } catch (FileNotFoundException e) {
            System.err.println("Error: Metadata file not found.");
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (ClassNotFoundException | IOException e) {
            System.err.println("Error: Metadata for the logged execution is corrupted.");
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }

}
