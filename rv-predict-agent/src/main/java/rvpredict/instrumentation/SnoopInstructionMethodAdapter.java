package rvpredict.instrumentation;

import static org.objectweb.asm.Opcodes.*;
import static rvpredict.config.Config.*;
import static rvpredict.instrumentation.Utility.*;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import rvpredict.config.Config;

public class SnoopInstructionMethodAdapter extends MethodVisitor {

    private final String className;
    private final int version;
    private final String source;
    private final String methodName;
    private final String signature;

    /**
     * Specifies whether the visited method is an initialization method.
     */
    private final boolean isInit;

    /**
     * Specifies whether the visited method is synchronized.
     */
    private final boolean isSynchronized;

    /**
     * Specifies whether the visited method is static.
     */
    private final boolean isStatic;

    private final Config config;

    /**
     * current max index of local variables
     */
    private int crntMaxIndex;
    private int crntLineNum;

    public SnoopInstructionMethodAdapter(MethodVisitor mv, String source, String className,
            int version, String methodName, String signature, int access, int argSize,
            Config config) {
        super(Opcodes.ASM5, mv);
        this.source = source == null ? "Unknown" : source;
        this.className = className;
        this.version = version;
        this.methodName = methodName;
        this.signature = signature;
        this.isInit = "<init>".equals(methodName) || "<clinit>".equals(methodName);
        this.isSynchronized = (access & ACC_SYNCHRONIZED) != 0;
        this.isStatic = (access & ACC_STATIC) != 0;
        this.config = config;

        crntMaxIndex = argSize + 1;
        if (config.verbose) {
            System.out.println("method: " + this.methodName);
        }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        mv.visitMaxs(maxStack + 6, crntMaxIndex + 2);// may change to ...
    }

    @Override
    public void visitVarInsn(int opcode, int localVarIdx) {
        crntMaxIndex = Math.max(localVarIdx, crntMaxIndex);

        switch (opcode) {
        case LSTORE: case DSTORE: case LLOAD: case DLOAD:
            /* double words load/store opcodes */
            crntMaxIndex = Math.max(crntMaxIndex, localVarIdx + 1);
        case ISTORE: case FSTORE: case ASTORE: case ILOAD: case FLOAD: case ALOAD:
        case RET:
            /* single word load/store and ret opcodes */
            mv.visitVarInsn(opcode, localVarIdx);
            break;
        default:
            assert false : "Unknown var instruction opcode " + opcode;
        }
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        crntLineNum = line;
        mv.visitLineNumber(line, start);
    }

    /**
     * Substitutes method call with its counterpart method provided by the
     * RV-Predict runtime.
     *
     * @param opcode
     *            INVOKEVIRTUAL or INVOKESTATIC
     * @param substName
     *            the name of the substitution method
     * @param substDesc
     *            the descriptor of the substitution method
     * @param argDesc
     *            argument type descriptors of the original method
     */
    private void substituteMethodCall(int opcode, String substName, String substDesc,
            String... argDesc) {
        assert opcode == INVOKEVIRTUAL || opcode == INVOKESTATIC;

        // <stack>... (objectref)? (arg)* </stack>
        int[] indices = new int[argDesc.length];
        for (int i = argDesc.length - 1; i >= 0; i--) {
            indices[i] = storeValue(argDesc[i]);
        }
        int objRefIndex = opcode == INVOKEVIRTUAL ? astore() : -1;
        // <stack>... </stack>
        addPushConstInsn(mv, getCrntStmtSID());
        if (opcode == INVOKEVIRTUAL) {
            mv.visitVarInsn(ALOAD, objRefIndex);
        }
        for (int i = 0; i < argDesc.length; i++) {
            loadValue(argDesc[i], indices[i]);
        }
        // <stack>... sid (objectref)? (arg)* </stack>
        invokeStatic(substName, substDesc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        if (opcode == INVOKEVIRTUAL) {
            switch (name + desc) {
            case "start()V":
                if (isThreadClass(owner)) {
                    substituteMethodCall(opcode, RVPREDICT_THREAD_START, DESC_RVPREDICT_THREAD_START);
                    return;
                }
                break;
            case "join()V":
                if (isThreadClass(owner)) {
                    substituteMethodCall(opcode, RVPREDICT_JOIN, DESC_RVPREDICT_JOIN);
                    return;
                }
                break;
            case "join(J)V":
                if (isThreadClass(owner)) {
                    substituteMethodCall(opcode, RVPREDICT_JOIN, DESC_RVPREDICT_JOIN_TIMEOUT, "J");
                    return;
                }
                break;
            case "join(JI)V":
                if (isThreadClass(owner)) {
                    substituteMethodCall(opcode, RVPREDICT_JOIN, DESC_RVPREDICT_JOIN_TIMEOUT_NANO,
                            "J", "I");
                    return;
                }
                break;
            case "interrupt()V":
                if (isThreadClass(owner)) {
                    substituteMethodCall(opcode, RVPREDICT_INTERRUPT, DESC_RVPREDICT_INTERRUPT);
                    return;
                }
                break;
            case "isInterrupted()Z":
                if (isThreadClass(owner)) {
                    substituteMethodCall(opcode, RVPREDICT_IS_INTERRUPTED,
                            DESC_RVPREDICT_IS_INTERRUPTED);
                    return;
                }
                break;
            case "wait()V":
                substituteMethodCall(opcode, RVPREDICT_WAIT, DESC_RVPREDICT_WAIT);
                return;
            case "wait(J)V":
                substituteMethodCall(opcode, RVPREDICT_WAIT, DESC_RVPREDICT_WAIT_TIMEOUT, "J");
                return;
            case "wait(JI)V":
                substituteMethodCall(opcode, RVPREDICT_WAIT, DESC_RVPREDICT_WAIT_TIMEOUT_NANO, "J",
                        "I");
                return;
            case "notify()V":
                substituteMethodCall(opcode, RVPREDICT_NOTIFY, DESC_RVPREDICT_NOTIFY);
                return;
            case "notifyAll()V":
                substituteMethodCall(opcode, RVPREDICT_NOTIFY_ALL, DESC_RVPREDICT_NOTIFY_ALL);
                return;
            }
        } else if (opcode == INVOKESTATIC) {
            switch (owner + name + desc) {
            case "java/lang/Thread/interrupted()Z":
                substituteMethodCall(opcode, RVPREDICT_INTERRUPTED, DESC_RVPREDICT_INTERRUPTED);
                return;
            case "java/lang/Thread/sleep(J)V":
                substituteMethodCall(opcode, RVPREDICT_SLEEP, DESC_RVPREDICT_SLEEP, "J");
                return;
            case "java/lang/Thread/sleep(JI)V":
                substituteMethodCall(opcode, RVPREDICT_SLEEP, DESC_RVPREDICT_SLEEP_NANOS, "J", "I");
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        int sid = GlobalStateForInstrumentation.getVariableId(owner, name);
        String varSig = (owner + "." + name).replace("/", ".");
        // TODO(YilongL): move the following code to GlobalStateForInstrumentation
        String sig_loc = source
                + "|"
                + (className + "|" + signature + "|" + varSig + "|" + crntLineNum).replace("/",
                        ".");
        int ID = GlobalStateForInstrumentation.getLocationId(sig_loc);

        int localVarIdx;
        int localVarIdx2;
        switch (opcode) {
        case GETSTATIC:
            if (isInit) {
                mv.visitFieldInsn(opcode, owner, name, desc);
                return;
            }

            // <stack>... </stack>
            mv.visitFieldInsn(opcode, owner, name, desc);
            // <stack>... value </stack>
            localVarIdx = dupThenStoreValue(desc); // jvm_local_vars[localVarIdx] = value
            // <stack>... value </stack>
            addPushConstInsn(mv, ID);
            mv.visitInsn(ACONST_NULL);
            addPushConstInsn(mv, sid);
            loadThenBoxValue(desc, localVarIdx);
            addPushConstInsn(mv, 0);
            addPushConstInsn(mv, config.commandLine.branch ? 1 : 0);
            // <stack>... value ID null sid value false branch </stack>
            invokeStatic(LOG_FIELD_ACCESS, DESC_LOG_FIELD_ACCESS);
            // <stack>... value </stack>
            break;
        case PUTSTATIC:
            // <stack>... value </stack>
            localVarIdx = dupThenStoreValue(desc); // jvm_local_vars[localVarIdx] = value
            // <stack>... value </stack>
            mv.visitFieldInsn(opcode, owner, name, desc);
            // <stack>... </stack>
            addPushConstInsn(mv, ID);
            mv.visitInsn(ACONST_NULL);
            addPushConstInsn(mv, sid);
            loadThenBoxValue(desc, localVarIdx);
            // <stack>... ID null sid value </stack>

            if (isInit)
                invokeStatic(LOG_FIELD_INIT, DESC_LOG_FIELD_INIT);
            else {
                addPushConstInsn(mv, 1);
                addPushConstInsn(mv, config.commandLine.branch ? 1 : 0);
                // <stack>... ID null sid value false branch </stack>
                invokeStatic(LOG_FIELD_ACCESS, DESC_LOG_FIELD_ACCESS);
            }
            break;
        case GETFIELD:
            if (isInit) {
                mv.visitFieldInsn(opcode, owner, name, desc);
                return;
            }

            // <stack>... objectref </stack>
            localVarIdx = dupThenAStore(); // jvm_local_vars[localVarIdx] = objectref
            mv.visitFieldInsn(opcode, owner, name, desc);
            // <stack>... value </stack>
            localVarIdx2 = dupThenStoreValue(desc); // jvm_local_vars[localVarIdx2] = value
            // <stack>... value </stack>
            addPushConstInsn(mv, ID);
            mv.visitVarInsn(ALOAD, localVarIdx);
            addPushConstInsn(mv, sid);
            loadThenBoxValue(desc, localVarIdx2);
            addPushConstInsn(mv, 0);
            addPushConstInsn(mv, config.commandLine.branch ? 1 : 0);
            // <stack>... value ID objectref sid value false branch </stack>
            invokeStatic(LOG_FIELD_ACCESS, DESC_LOG_FIELD_ACCESS);
            // <stack>... value </stack>
            break;
        case PUTFIELD:
            // TODO(YilongL): why don't we instrument inner class fields?
            if (name.startsWith("this$")) { // inner class
                mv.visitFieldInsn(opcode, owner, name, desc);
                break;
            }

            // TODO(YilongL): what is this?
            if (className.contains("$") && name.startsWith("val$")) { // strange class
                mv.visitFieldInsn(opcode, owner, name, desc);
                break;
            }

            // <stack>... objectref value </stack>
            localVarIdx = storeValue(desc); // jvm_local_vars[localVarIdx] = value
            // <stack>... objectref </stack>
            localVarIdx2 = dupThenAStore(); // jvm_local_vars[localVarIdx2] = objectref
            // <stack>... objectref </stack>
            loadValue(desc, localVarIdx);
            // <stack>... objectref value </stack>
            mv.visitFieldInsn(opcode, owner, name, desc);
            // <stack>... </stack>
            addPushConstInsn(mv, ID);
            mv.visitVarInsn(ALOAD, localVarIdx2);
            addPushConstInsn(mv, sid);
            loadThenBoxValue(desc, localVarIdx);
            // <stack>... ID objectref sid value </stack>
            if (isInit)
                invokeStatic(LOG_FIELD_INIT, DESC_LOG_FIELD_INIT);
            else {
                addPushConstInsn(mv, 1);
                addPushConstInsn(mv, config.commandLine.branch ? 1 : 0);
                // <stack>... ID objectref sid value true branch </stack>
                invokeStatic(LOG_FIELD_ACCESS, DESC_LOG_FIELD_ACCESS);
            }
            // <stack>... </stack>
            break;
        default:
            System.err.println("Unknown field access opcode " + opcode);
            System.exit(1);
        }
    }

    @Override
    public void visitInsn(int opcode) {
        switch (opcode) {
        case AALOAD: case BALOAD: case CALOAD: case SALOAD:
        case IALOAD: case FALOAD: case DALOAD: case LALOAD:
            instrumentArrayLoad(opcode);
            break;
        case AASTORE: case BASTORE: case CASTORE: case SASTORE:
        case IASTORE: case FASTORE: case DASTORE: case LASTORE:
            instrumentArrayStore(opcode);
            break;
        case MONITORENTER: case MONITOREXIT: {
            int sid = getCrntStmtSID();
            // <stack>... objectref </stack>
            int index = dupThenAStore(); // jvm_local_vars[index] = objectref
            // <stack>... objectref </stack>
            addPushConstInsn(mv, sid);
            mv.visitVarInsn(ALOAD, index);
            // <stack>... objectref sid objectref </stack>
            if (opcode == MONITORENTER) {
                invokeStatic(LOG_LOCK, DESC_LOG_LOCK);
            } else {
                invokeStatic(LOG_UNLOCK, DESC_LOG_UNLOCK);
            }
            // <stack>... objectref </stack>
            mv.visitInsn(opcode);
            break;
        }
        case IRETURN: case LRETURN: case FRETURN: case DRETURN:
        case ARETURN: case RETURN: case ATHROW:
            if (isSynchronized) {
                /* Add a runtime library callback to log {@code UNLOCK} event for synchronized method. */
                addPushConstInsn(mv, getCrntStmtSID());
                if (isStatic) {
                    loadClassLiteral();
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                }
                invokeStatic(LOG_UNLOCK, DESC_LOG_UNLOCK);
            }
            mv.visitInsn(opcode);
            break;
        default:
            mv.visitInsn(opcode);
            break;
        }
    }

    private void instrumentArrayLoad(int arrayLoadOpcode) {
        if (isInit) {
            mv.visitInsn(arrayLoadOpcode);
            return;
        }

        boolean isElemSingleWord = isElementSingleWord(arrayLoadOpcode);
        int sid = getCrntStmtSID();

        // <stack>... arrayref index </stack>
        mv.visitInsn(DUP2);
        // <stack>... arrayref index arrayref index </stack>
        int localVarIdx1 = istore(); // jvm_local_vars[localVarIdx1] = index
        int localVarIdx2 = astore(); // jvm_local_vars[localVarIdx2] = arrayref
        // <stack>... arrayref index </stack>
        mv.visitInsn(arrayLoadOpcode);
        // <stack>... value </stack>, where `value` could be one word or two words
        if (isElemSingleWord) {
            mv.visitInsn(DUP);
        } else {
            mv.visitInsn(DUP2);
        }
        // <stack>... value value </stack>
        int localVarIdx3 = ++crntMaxIndex;
        if (!isElemSingleWord) {
            crntMaxIndex++;
        }
        mv.visitVarInsn(getElementStoreOpcode(arrayLoadOpcode), localVarIdx3); // jvm_local_vars[localVarIdx3] = value
        // <stack>... value </stack>

        addPushConstInsn(mv, sid);
        mv.visitVarInsn(ALOAD, localVarIdx2);
        mv.visitVarInsn(ILOAD, localVarIdx1);
        mv.visitVarInsn(getElementLoadOpcode(arrayLoadOpcode), localVarIdx3);
        // <stack>... value sid arrayref index value </stack>

        if (arrayLoadOpcode != AALOAD) {
            addPrimitive2ObjectConv(mv, arrayLoadOpcode);
        }
        // <stack>... value sid arrayref index valueObjRef </stack>

        addPushConstInsn(mv, 0);
        // <stack>... value sid arrayref index valueObjRef false </stack>

        invokeStatic(LOG_ARRAY_ACCESS, DESC_LOG_ARRAY_ACCESS);
        // <stack>... value </stack>
    }

    private void instrumentArrayStore(int arrayStoreOpcode) {
        boolean isElemSingleWord = isElementSingleWord(arrayStoreOpcode);
        int sid = getCrntStmtSID();

        // <stack>... arrayref index value </stack>, where value could be one word or two words
        int localVarIdx1 = ++crntMaxIndex;
        mv.visitVarInsn(getElementStoreOpcode(arrayStoreOpcode), localVarIdx1); // jvm_local_vars[localVarIdx1] = value
        if (!isElemSingleWord) {
            crntMaxIndex++;
        }
        // <stack>... arrayref index </stack>
        mv.visitInsn(DUP2);
        // <stack>... arrayref index arrayref index </stack>
        int localVarIdx2 = istore(); // jvm_local_vars[localVarIdx2] = index
        // <stack>... arrayref index arrayref </stack>
        int localVarIdx3 = astore(); // jvm_local_vars[localVarIdx3] = arrayref
        // <stack>... arrayref index </stack>
        mv.visitVarInsn(getElementLoadOpcode(arrayStoreOpcode), localVarIdx1);
        // <stack>... arrayref index value </stack>
        mv.visitInsn(arrayStoreOpcode);
        // <stack>... </stack>
        addPushConstInsn(mv, sid);
        mv.visitVarInsn(ALOAD, localVarIdx3);
        mv.visitVarInsn(ILOAD, localVarIdx2);
        mv.visitVarInsn(getElementLoadOpcode(arrayStoreOpcode), localVarIdx1);
        // <stack>... sid arrayref index value </stack>
        if (arrayStoreOpcode != AASTORE) {
            addPrimitive2ObjectConv(mv, arrayStoreOpcode);
        }
        // <stack>... sid arrayref index valueObjRef </stack>
        if (isInit) {
            invokeStatic(LOG_ARRAY_INIT, DESC_LOG_ARRAY_INIT);
        } else {
            addPushConstInsn(mv, 1);
            // <stack>... sid arrayref index valueObjRef true </stack>
            invokeStatic(LOG_ARRAY_ACCESS, DESC_LOG_ARRAY_ACCESS);
        }
        // <stack>... </stack>
    }

    @Override
    public void visitCode() {
        if (isSynchronized) {
            /* Add a runtime library callback to log {@code LOCK} event for synchronized method. */
            addPushConstInsn(mv, getCrntStmtSID());
            if (isStatic) {
                loadClassLiteral();
            } else {
                mv.visitVarInsn(ALOAD, 0);
            }
            invokeStatic(LOG_LOCK, DESC_LOG_LOCK);
        }

        mv.visitCode();
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (config.commandLine.branch) {
            if (opcode != JSR && opcode != GOTO) {
                addPushConstInsn(mv, getCrntStmtSID());
                invokeStatic(LOG_BRANCH, DESC_LOG_BRANCH);
            }
        }
        mv.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        if (config.commandLine.branch) {
            addPushConstInsn(mv, getCrntStmtSID());
            invokeStatic(LOG_BRANCH, DESC_LOG_BRANCH);
        }
        mv.visitTableSwitchInsn(min, max, dflt, labels);
    }

    private void invokeStatic(String methodName, String methodDescriptor) {
        mv.visitMethodInsn(INVOKESTATIC, config.logClass, methodName, methodDescriptor, false);
    }

    /**
     * Stores the top value from the operand stack to the local variable array.
     *
     * @param desc
     *            the type descriptor of the value to store
     * @return the local variable index that stores the value
     */
    private int storeValue(String desc) {
        int localVarIdx = ++crntMaxIndex;
        int opcode = Type.getType(desc).getOpcode(ISTORE);
        mv.visitVarInsn(opcode, localVarIdx);
        if (isDoubleWordTypeDesc(desc)) {
            crntMaxIndex++;
        }
        return localVarIdx;
    }

    /**
     * Duplicates and then stores the top value from the operand stack to the
     * local variable array.
     *
     * @param desc
     *            the type descriptor of the value to store
     * @return the local variable index that stores the value
     */
    private int dupThenStoreValue(String desc) {
        int localVarIdx = ++crntMaxIndex;
        int opcode = Type.getType(desc).getOpcode(ISTORE);
        if (isSingleWordTypeDesc(desc)) {
            mv.visitInsn(DUP);
            mv.visitVarInsn(opcode, localVarIdx);
        } else {
            mv.visitInsn(DUP2);
            mv.visitVarInsn(opcode, localVarIdx);
            crntMaxIndex++;
        }
        return localVarIdx;
    }

    private int dupThenAStore() {
        int localVarIdx = ++crntMaxIndex;
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, localVarIdx);
        return localVarIdx;
    }

    private int astore() {
        int localVarIdx = ++crntMaxIndex;
        mv.visitVarInsn(ASTORE, localVarIdx);
        return localVarIdx;
    }

    private int istore() {
        int localVarIdx = ++crntMaxIndex;
        mv.visitVarInsn(ISTORE, localVarIdx);
        return localVarIdx;
    }

    /**
     * Loads the value from the local variable array, boxes it, and puts it on
     * top of the operand stack.
     *
     * @param desc
     *            the type descriptor of the value to load
     * @param index
     *            the local variable index that has the value
     */
    private void loadThenBoxValue(String desc, int index) {
        loadValue(desc, index);
        if (isPrimitiveTypeDesc(desc)) {
            addPrimitive2ObjectConv(mv, desc);
        }
    }

    /**
     * Loads the value from the local variable array and puts it on top of the
     * operand stack.
     *
     * @param desc
     *            the type descriptor of the value to load
     * @param index
     *            the local variable index that has the value
     */
    private void loadValue(String desc, int index) {
        mv.visitVarInsn(Type.getType(desc).getOpcode(ILOAD), index);
    }

    private void loadClassLiteral() {
        /* before Java 5 the bytecode for loading class literal is quite cumbersome */
        if (version < 49) {
            /* `class$` is a special method generated to compute class literal */
            String fieldName = "class$" + className.replace('/', '$');
            mv.visitFieldInsn(GETSTATIC, className, fieldName, DESC_CLASS);
            Label l0 = new Label();
            mv.visitJumpInsn(IFNONNULL, l0);
            mv.visitLdcInsn(className.replace('/', '.'));
            mv.visitMethodInsn(INVOKESTATIC, className, "class$", String.format("(%s)%s", DESC_STRING, DESC_CLASS), false);
            mv.visitInsn(DUP);
            mv.visitFieldInsn(PUTSTATIC, className, fieldName, DESC_CLASS);
            Label l1 = new Label();
            mv.visitJumpInsn(GOTO, l1);
            mv.visitLabel(l0);
            mv.visitFieldInsn(GETSTATIC, className, fieldName, DESC_CLASS);
            mv.visitLabel(l1);
        } else {
            mv.visitLdcInsn(Type.getObjectType(className));
        }
    }


    /**
     * @return a unique integer representing the syntactic identifier of the
     *         current statement in the instrumented program
     */
    private int getCrntStmtSID() {
        return GlobalStateForInstrumentation.getLocationId(getCrntStmtSig());
    }

    /**
     * @return a unique string representing the signature of the current
     *         statement in the instrumented program
     */
    private String getCrntStmtSig() {
        // TODO(YilongL): is the replace really necessary?
        return String.format("%s|%s|%s|%s", source, className, signature, crntLineNum).replace("/", ".");
    }
}
