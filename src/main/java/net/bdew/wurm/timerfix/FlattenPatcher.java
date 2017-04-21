package net.bdew.wurm.timerfix;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.*;

import java.util.logging.Logger;

public class FlattenPatcher {
    private static final Logger logger = Logger.getLogger("FlattenPatcher");

    public static void patchFlatten(ClassPool classPool) {
        try {
            CtClass ctFlattening = classPool.getCtClass("com.wurmonline.server.behaviours.Flattening");
            CtMethod ctFlatten = ctFlattening.getMethod("flatten", "(JLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIIIIFLcom/wurmonline/server/behaviours/Action;)Z");

            // Do dark bytecode voodoo
            doPatch(ctFlatten);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    private static int findNextOp(int op, CodeIterator ci) throws BadBytecode {
        while (ci.hasNext()) {
            int pos = ci.next();
            if (ci.byteAt(pos) == op)
                return pos;
        }
        throw new RuntimeException("Bytecode not found");
    }

    private static void writeCall(CodeIterator ci, ConstPool cp, int instaVar, int counterVar, int typeVar, int start, int next, int endif, boolean first) {
        Bytecode newCode = new Bytecode(cp);

        // Action is already on stack, put the rest of the stuff we need
        newCode.addIload(instaVar);
        newCode.addFload(counterVar);
        newCode.addIload(typeVar);
        newCode.addIconst(first ? 1 : 0);
        newCode.addInvokestatic("net.bdew.wurm.timerfix.TimerHooks", "shouldFlattenTick", "(Lcom/wurmonline/server/behaviours/Action;ZFBZ)Z");

        // jump to the original end if false
        newCode.add(Bytecode.IFEQ);
        newCode.addIndex(endif - (start + newCode.currentPc() - 1));

        // dummy out all the remaining crap
        while (start + newCode.currentPc() < next)
            newCode.add(Bytecode.NOP);

        ci.write(newCode.get(), start);
    }

    private static void doPatch(CtMethod m) throws BadBytecode {
        MethodInfo mi = m.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        ConstPool constPool = ca.getConstPool();
        CodeIterator codeIterator = ca.iterator();

        // Local variable numbers
        int actionVar = -1;
        int instaVar = -1;
        int counterVar = -1;
        int typeVar = -1;

        boolean appliedPatch1 = false, appliedPatch2 = false, appliedActionControl = false;

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();
            int op = codeIterator.byteAt(pos);
            if (op == CodeIterator.INVOKESTATIC) {
                int ref = codeIterator.u16bitAt(pos + 1);
                String methodName = constPool.getMethodrefName(ref);
                if (methodName.equals("decodeType")) {
                    pos = codeIterator.next();
                    op = codeIterator.byteAt(pos);
                    if (op == CodeIterator.ISTORE) {
                        typeVar = codeIterator.byteAt(pos + 1);
                        break;
                    }
                }
            }
        }

        if (typeVar == -1) throw new RuntimeException("Type local variable not found");

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();
            int op = codeIterator.byteAt(pos);
            if (op == CodeIterator.ALOAD)
                actionVar = codeIterator.byteAt(pos + 1);
            else if (op == CodeIterator.INVOKEVIRTUAL) {
                int ref = codeIterator.u16bitAt(pos + 1);
                String methodName = constPool.getMethodrefName(ref);
                if (methodName.equals("currentSecond")) {
                    int start = pos;
                    pos = findNextOp(CodeIterator.ILOAD, codeIterator);
                    instaVar = codeIterator.byteAt(pos + 1);
                    pos = findNextOp(CodeIterator.FLOAD, codeIterator);
                    counterVar = codeIterator.byteAt(pos + 1);
                    pos = findNextOp(CodeIterator.IFNE, codeIterator);
                    // Get absolute address of whatever executes after this block
                    int endif = codeIterator.u16bitAt(pos + 1) + pos;
                    int next = codeIterator.next();
                    logger.info(String.format("Vars are act=%d insta=%d counter=%d type=%d", actionVar, instaVar, counterVar, typeVar));
                    logger.info(String.format("First check matched start=%d next=%d endif=%d", start, next, endif));

                    writeCall(codeIterator, constPool, instaVar, counterVar, typeVar, start, next, endif, true);

                    appliedPatch1 = true;
                    break;
                }
            }
        }

        if (!appliedPatch1) throw new RuntimeException("Flatten patch application failed");

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();
            int op = codeIterator.byteAt(pos);
            if (op == CodeIterator.INVOKEVIRTUAL) {
                int ref = codeIterator.u16bitAt(pos + 1);
                if (constPool.getMethodrefName(ref).equals("sendActionControl")) {
                    appliedActionControl = true;
                    Bytecode newCode = new Bytecode(constPool);
                    newCode.add(Bytecode.I2F);
                    newCode.addGetstatic("com.wurmonline.server.Servers", "localServer", "Lcom/wurmonline/server/ServerEntry;");
                    newCode.addInvokevirtual("com.wurmonline.server.ServerEntry", "getActionTimer", "()F");
                    newCode.add(Bytecode.FDIV);
                    newCode.add(Bytecode.F2I);
                    codeIterator.move(pos);
                    codeIterator.insert(newCode.get());
                    logger.info(String.format("sendActionControl patched at %d", pos));
                    break;
                }
            }
        }

        if (!appliedActionControl) throw new RuntimeException("Flatten patch application failed");

        while (codeIterator.hasNext()) {
            int pos = codeIterator.next();
            int op = codeIterator.byteAt(pos);
            if (op == CodeIterator.INVOKEVIRTUAL) {
                int ref = codeIterator.u16bitAt(pos + 1);
                String methodName = constPool.getMethodrefName(ref);
                if (methodName.equals("currentSecond")) {
                    int start = pos;
                    // skip one
                    findNextOp(CodeIterator.IFEQ, codeIterator);
                    // next one is the final check
                    pos = findNextOp(CodeIterator.IFEQ, codeIterator);
                    // Get absolute address of whatever executes after this block
                    int endif = codeIterator.u16bitAt(pos + 1) + pos;
                    int next = codeIterator.next();

                    logger.info(String.format("Second check matched start=%d next=%d endif=%d", start, next, endif));

                    writeCall(codeIterator, constPool, instaVar, counterVar, typeVar, start, next, endif, false);
                    appliedPatch2 = true;
                    break;
                }
            }
        }

        if (!appliedPatch2) throw new RuntimeException("Flatten patch application failed");
    }
}

