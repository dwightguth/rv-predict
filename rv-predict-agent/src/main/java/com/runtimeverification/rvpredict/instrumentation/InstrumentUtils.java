package com.runtimeverification.rvpredict.instrumentation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.runtimeverification.rvpredict.config.Configuration;
import com.runtimeverification.rvpredict.metadata.ClassFile;

public class InstrumentUtils implements Opcodes {

    public static final Type OBJECT_TYPE    = Type.getObjectType("java/lang/Object");
    public static final Type CLASS_TYPE     = Type.getObjectType("java/lang/Class");
    public static final Type JL_FLOAT_TYPE  = Type.getObjectType("java/lang/Float");
    public static final Type JL_DOUBLE_TYPE = Type.getObjectType("java/lang/Double");
    public static final Type JL_SYSTEM_TYPE = Type.getObjectType("java/lang/System");
    public static final Type RVPREDICT_RUNTIME_TYPE = Type
            .getObjectType("com/runtimeverification/rvpredict/runtime/RVPredictRuntime");

    /**
     * Checks if one class or interface extends or implements another class or
     * interface.
     *
     * @param loader
     *            the initiating loader of {@code class0}, may be null if it is
     *            the bootstrap class loader or unknown
     * @param class0
     *            the name of the first class or interface
     * @param class1
     *            the name of the second class of interface
     * @return {@code true} if {@code class1} is assignable from {@code class0}
     */
    public static boolean isSubclassOf(ClassLoader loader, String class0, String class1) {
        assert !class1.startsWith("[");

        if (class0.startsWith("[")) {
            return class1.equals("java/lang/Object");
        }
        if (class0.equals(class1)) {
            return true;
        }

        boolean itf = (ClassFile.getInstance(loader, class1).getAccess() & ACC_INTERFACE) != 0;
        Set<String> superclasses = getSuperclasses(class0, loader);
        if (!itf) {
            return superclasses.contains(class1);
        } else {
            boolean result = getInterfaces(class0, loader).contains(class1);
            for (String superclass : superclasses) {
                result = result || getInterfaces(superclass, loader).contains(class1);
            }
            return result;
        }
    }

    /**
     * Gets all superclasses of a class or interface.
     * <p>
     * The superclass of an interface will be the {@code Object}.
     *
     * @param className
     *            the internal name of a class or interface
     * @param loader
     *            the initiating loader of the class, may be null if it is the
     *            bootstrap class loader or unknown
     * @return set of superclasses
     */
    private static Set<String> getSuperclasses(String className, ClassLoader loader) {
        Set<String> result = new HashSet<>();
        while (className != null) {
            String superName = ClassFile.getInstance(loader, className).getSuperName();
            if (superName != null) {
                result.add(superName);
            }
            className = superName;
        }
        return result;
    }

    /**
     * Gets all implemented interfaces (including parent interfaces) of a class
     * or all parent interfaces of an interface.
     *
     * @param className
     *            the internal name of a class or interface
     * @param loader
     *            the initiating loader of the class, may be null if it is the
     *            bootstrap class loader or unknown
     * @return set of interfaces
     */
    private static Set<String> getInterfaces(String className, ClassLoader loader) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(className);
        while (!queue.isEmpty()) {
            className = queue.poll();
            List<String> interfaces = ClassFile.getInstance(loader, className)
                    .getInterfaces();
            for (String itf : interfaces) {
                if (result.add(itf)) {
                    queue.add(itf);
                }
            }
        }
        return result;
    }

    public static void printTransformedClassToFile(String cname, byte[] cbuf, String dir) {
        String fileName = dir + "/" + cname.substring(cname.lastIndexOf("/") + 1) + ".class";
        File f = new File(fileName);

        try {
            OutputStream out = new FileOutputStream(f);
            out.write(cbuf);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Boolean> instrumentClass = new ConcurrentHashMap<>();

    /**
     * Checks if we should instrument a class or interface.
     *
     * @param classFile
     *
     * @return {@code true} if we should instrument it; otherwise, {@code false}
     */
    public static boolean needToInstrument(ClassFile classFile) {
        String cname = classFile.getClassName();
        ClassLoader loader = classFile.getLoader();

        Boolean toInstrument = instrumentClass.get(cname);
        if (toInstrument != null) {
            return toInstrument;
        }

        toInstrument = true;
        for (Pattern exclude : Agent.config.excludeList) {
            toInstrument = !exclude.matcher(cname).matches();
            if (!toInstrument) break;
        }

        if (toInstrument) {
            for (String mock : Configuration.MOCKS) {
                if (isSubclassOf(loader, cname, mock)) {
                    toInstrument = false;
                    if (Configuration.verbose) {
                        /* TODO(YilongL): this may cause missing data races if
                         * the mock for interface/superclass does not contain
                         * methods specific to this implementation. This could
                         * be a big problem if the application makes heavy use
                         * of helper methods specific in some high-level
                         * concurrency library (e.g. Guava) while most of the
                         * classes are simply excluded here */
                        System.err.println("[Java-agent] excluded " + cname
                                + " from instrumentation because we are mocking " + mock);
                    }
                    break;
                }
            }
        }

        if (!toInstrument) {
            /* include list overrides the above */
            for (Pattern include : Agent.config.includeList) {
                toInstrument = include.matcher(cname).matches();
                if (toInstrument) break;
            }
        }

        /* make sure we don't instrument IGNORES even if the user said so */
        if (toInstrument) {
            for (Pattern ignore : Configuration.IGNORES) {
                toInstrument = !ignore.matcher(cname).matches();
                if (!toInstrument) break;
            }
        }

        if (!toInstrument) {
            for (Pattern include : Configuration.MUST_INCLUDES) {
                toInstrument = include.matcher(cname).matches();
                if (toInstrument) break;
            }
        }

        instrumentClass.put(cname, toInstrument);
        return toInstrument;
    }

}
