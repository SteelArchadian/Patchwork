package dev.chill.patchwork.fabricattributes;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabric mods run through Connector (Better Nether, Better End, ...) give their mobs attributes with Fabric API's
 * FabricDefaultAttributeRegistry.register while the game is still starting. That call opens vanilla's DefaultAttributes
 * table, which loads every vanilla mob class it lists. A NeoForge mod that hooks one of those classes and reads its own
 * registry there (Atmospheric's camel variants) then fails, because NeoForge has not set its registries up yet, and the
 * game crashes. Registrations made that early are held here and handed to Fabric API at the start of NeoForge's own
 * attribute step, the point where NeoForge mods give theirs. Same attributes, same result.
 */
public final class EarlyAttributes {
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<Object[]> held = new ArrayList<>();
    private static boolean ready;

    private EarlyAttributes() {}

    /** True when the registration was held (too early); the caller then skips its own work. */
    public static synchronized boolean hold(EntityType<? extends LivingEntity> type, AttributeSupplier supplier) {
        if (ready) {
            return false;
        }
        held.add(new Object[] {type, supplier});
        return true;
    }

    /** NeoForge's attribute step is starting: registries are set up, so the held registrations can go through. */
    public static synchronized void release() {
        if (ready) {
            return;
        }
        ready = true;
        if (held.isEmpty()) {
            return;
        }
        try {
            Method register = Class.forName("net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry")
                    .getMethod("register", EntityType.class, AttributeSupplier.class);
            for (Object[] entry : held) {
                register.invoke(null, entry[0], entry[1]);
            }
            LOG.info("[Patchwork] fabricattributes: {} early Fabric mob attribute registration(s) applied at NeoForge's attribute step", held.size());
        } catch (ReflectiveOperationException e) {
            LOG.error("[Patchwork] fabricattributes: could not apply held Fabric mob attributes", e);
        }
        held.clear();
    }
}
