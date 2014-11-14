package asm;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodWriterTest {

    @Ignore("ASM Bug: http://forge.ow2.org/tracker/?func=detail&aid=317547&group_id=23&atid=100023")
    @Test
    public void test() throws IOException {
        ClassReader cr = new ClassReader(ClassB.class.getName());
        ClassWriter cw = new ClassWriter(cr, 0);
        StringBuilder resultBuilder = new StringBuilder();
        cr.accept(new MyClassVisitor(Opcodes.ASM5, cw, resultBuilder), 0);
        Assert.assertEquals("asm/ClassB|asm/ClassA|asm/ClassB|asm/ClassB|asm/ClassB|asm/ClassA|",
                resultBuilder.toString());
    }

}

class MyClassVisitor extends ClassVisitor {

    private final StringBuilder resultBuilder;

    public MyClassVisitor(int api, ClassVisitor cv, StringBuilder resultBuilder) {
        super(api, cv);
        this.resultBuilder = resultBuilder;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        return new FieldInsnPrinter(api, mv, resultBuilder);
    }

}

class FieldInsnPrinter extends MethodVisitor {

    private final StringBuilder resultBuilder;

    public FieldInsnPrinter(int api, MethodVisitor mv, StringBuilder resultBuilder) {
        super(api, mv);
        this.resultBuilder = resultBuilder;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
//        System.err.println(owner + "." + name);
        resultBuilder.append(owner + "|");
        mv.visitFieldInsn(opcode, owner, name, desc);
    }

}
