package dev.chill.patchwork.irispathchar;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Mixin merges every overwrite before it applies any injector, so Iris's cancellable inject at the head of
 * ResourceLocation.validPathChar lands on top of Patchwork's overwrite and still makes a CallbackInfoReturnable per
 * character. Patchwork's mixin plugin runs this after all mixins are applied to ResourceLocation: the method body
 * becomes a call to {@link #valid(char)}, the same rule Iris's inject and the overwrite implement. Iris's other head
 * inject, on isValidPath ("DUMMY" is never a valid path), is folded the same way into {@link #validPath(String)}.
 */
public final class PathCharRewrite {
    private PathCharRewrite() {}

    /** Vanilla's path characters plus A-Z (Iris lets shader packs use capitals). */
    public static boolean valid(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.'
                || c >= 'A' && c <= 'Z';
    }

    /** Vanilla's isValidPath with this module's characters, plus Iris's rule that "DUMMY" is never valid. */
    public static boolean validPath(String path) {
        if ("DUMMY".equals(path)) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            if (!valid(path.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Returns true when either method still had an injected callback that was removed. */
    public static boolean apply(ClassNode target) {
        return rewrite(target, "validPathChar", "(C)Z", Opcodes.ILOAD, "valid")
                | rewrite(target, "isValidPath", "(Ljava/lang/String;)Z", Opcodes.ALOAD, "validPath");
    }

    private static boolean rewrite(ClassNode target, String name, String desc, int load, String replacement) {
        for (MethodNode method : target.methods) {
            if (!method.name.equals(name) || !method.desc.equals(desc)) {
                continue;
            }
            boolean hadCallback = false;
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.endsWith("CallbackInfoReturnable")) {
                    hadCallback = true;
                    break;
                }
            }
            InsnList body = new InsnList();
            body.add(new VarInsnNode(load, 0));
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/chill/patchwork/irispathchar/PathCharRewrite",
                    replacement, desc, false));
            body.add(new InsnNode(Opcodes.IRETURN));
            method.instructions = body;
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) {
                method.localVariables.clear();
            }
            method.maxStack = 1;
            method.maxLocals = 1;
            return hadCallback;
        }
        return false;
    }
}
