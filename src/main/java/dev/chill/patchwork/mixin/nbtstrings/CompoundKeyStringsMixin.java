package dev.chill.patchwork.mixin.nbtstrings;

import dev.chill.patchwork.nbtstrings.NbtStrings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.DataInput;
import java.io.IOException;

/**
 * Compound tag keys read from disk or the network ("Name", "Properties", "Palette", ...) come back as shared Strings
 * (see {@link NbtStrings}) instead of a new String and byte[] each. Same keys.
 */
@Mixin(targets = "net.minecraft.nbt.CompoundTag$1")
public abstract class CompoundKeyStringsMixin {
    @Redirect(method = {"loadCompound", "readString"}, require = 2,
            at = @At(value = "INVOKE", target = "Ljava/io/DataInput;readUTF()Ljava/lang/String;", remap = false))
    private static String patchwork$sharedKey(DataInput input) throws IOException {
        return NbtStrings.readUTF(input);
    }
}
