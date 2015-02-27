package com.runtimeverification.rvpredict.instrumentation.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ClassTransformer extends ClassVisitor implements Opcodes {

    private final ClassLoader loader;

    private String className;
    private String source;

    private int version;

    public static byte[] transform(ClassLoader loader, byte[] cbuf) {
        ClassReader cr = new ClassReader(cbuf);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassTransformer transformer = new ClassTransformer(cw, loader);
        cr.accept(transformer, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private ClassTransformer(ClassWriter cw, ClassLoader loader) {
        super(ASM5, cw);
        assert cw != null;

        this.loader = loader;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        className = name;
        this.version = version;
        cv.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitSource(String source, String debug) {
        this.source = source;
        cv.visitSource(source, debug);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        assert mv != null;

        mv = new ExceptionHandlerSorter(mv, access, name, desc, signature, exceptions);

        /* do not instrument synthesized bridge method; otherwise, it may cause
         * infinite recursion at runtime */
        if ((access & ACC_BRIDGE) == 0) {
            mv = new MethodTransformer(mv, source, className, version, name, desc, access,
                    loader);
        }

        if ("<clinit>".equals(name)) {
            mv = new ClassInitializerTransformer(mv, access, name, desc);
        }

        return mv;
    }

}
