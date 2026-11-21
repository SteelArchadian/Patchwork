package dev.chill.patchwork.relativeblocks;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Runs from Patchwork's mixin plugin on MultiPlayerGameMode before any mod's mixins are applied to it (see
 * {@link MemberSound} for why). Plain ASM, no game classes, since it runs while the game class is being loaded.
 */
public final class MemberSoundRewrite {
    private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String SOUND = "Lnet/minecraft/world/level/block/SoundType;";
    private static final String NEO_DESC = "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)" + SOUND;

    private MemberSoundRewrite() {}

    /** Returns how many NeoForge getSoundType calls in continueDestroyBlock were rewritten. */
    public static int rewrite(ClassNode target) {
        int count = 0;
        for (MethodNode method : target.methods) {
            if (!method.name.equals("continueDestroyBlock")) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(STATE)
                        && call.name.equals("getSoundType") && call.desc.equals(NEO_DESC)) {
                    InsnList swap = new InsnList();
                    String self = "dev/chill/patchwork/relativeblocks/MemberSound";
                    swap.add(new MethodInsnNode(Opcodes.INVOKESTATIC, self, "remember",
                            "(L" + STATE + ";" + NEO_DESC.substring(1, NEO_DESC.indexOf(')')) + ")L" + STATE + ";", false));
                    swap.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, STATE, "getSoundType", "()" + SOUND, false));
                    AbstractInsnNode last = new MethodInsnNode(Opcodes.INVOKESTATIC, self, "pick", "(" + SOUND + ")" + SOUND, false);
                    swap.add(last);
                    method.instructions.insert(call, swap);
                    method.instructions.remove(call);
                    insn = last;
                    count++;
                }
            }
        }
        return count;
    }
}
