package dev.chill.patchwork.mixin.nbtstrings;

import dev.chill.patchwork.nbtstrings.NbtStrings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.DataInput;
import java.io.IOException;

/** String tag values (block names in chunk palettes, mostly) come back as shared Strings; see {@link NbtStrings}. */
@Mixin(targets = "net.minecraft.nbt.StringTag$1")
public abstract class StringValueStringsMixin {
    @Redirect(method = "readAccounted", require = 1,
            at = @At(value = "INVOKE", target = "Ljava/io/DataInput;readUTF()Ljava/lang/String;", remap = false))
    private static String patchwork$sharedValue(DataInput input) throws IOException {
        return NbtStrings.readUTF(input);
    }
}
