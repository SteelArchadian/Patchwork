package dev.chill.patchwork.matpackart;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Tells a plain panel texture (vanilla look, or a recolour like Mandalas) apart from resource-pack art that paints a
 * picture over the whole screen (Immersive Interfaces backpacks, barns, furnaces). Checks the one texture row that
 * Inventory Expanded stretches to fill the gap above its 3D mat: on a plain panel that row is one solid colour between
 * the side borders; on art it is a picture or see-through. Result is cached per texture, row and resource-pack list.
 */
public final class PanelArt {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Patchwork");
    private static final Map<String, Boolean> CACHE = new HashMap<>();
    private static int packsHash;

    private PanelArt() {}

    /** u0/u1/v are 0..1 texture coords of the stretched row; true means "art, do not stretch or use as footer". */
    public static boolean isArt(ResourceLocation texture, float u0, float u1, float v) {
        if (texture == null) return false;
        Minecraft mc = Minecraft.getInstance();
        int hash = mc.options.resourcePacks.hashCode();
        if (hash != packsHash) {
            CACHE.clear();
            packsHash = hash;
        }
        String key = texture + "|" + Math.round(u0 * 4096) + "|" + Math.round(u1 * 4096) + "|" + Math.round(v * 4096);
        Boolean known = CACHE.get(key);
        if (known != null) return known;
        boolean art = check(mc, texture, u0, u1, v);
        CACHE.put(key, art);
        return art;
    }

    private static boolean check(Minecraft mc, ResourceLocation texture, float u0, float u1, float v) {
        Optional<Resource> res = mc.getResourceManager().getResource(texture);
        if (res.isEmpty()) return false;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            int y = Math.min(h - 1, Math.max(0, (int) Math.floor(v * h)));
            int x0 = Math.max(0, (int) Math.floor(Math.min(u0, u1) * w));
            int x1 = Math.min(w, (int) Math.ceil(Math.max(u0, u1) * w));
            int border = Math.max(1, Math.round(4f * w / 256f));
            x0 += border;
            x1 -= border;
            if (x1 - x0 < 4) return false;
            int base = img.getPixelRGBA(x0 + (x1 - x0) / 2, y);
            int odd = 0, total = 0;
            for (int x = x0; x < x1; x++) {
                int p = img.getPixelRGBA(x, y);
                total++;
                if (((p >>> 24) & 0xFF) < 250 || far(p, base)) odd++;
            }
            boolean art = odd * 20 > total; // more than 5 % off-colour or see-through
            if (art) LOG.info("[Patchwork] matpackart: {} is pack art, plain mat edge used", texture);
            return art;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean far(int a, int b) {
        for (int s = 0; s < 24; s += 8) {
            if (Math.abs(((a >>> s) & 0xFF) - ((b >>> s) & 0xFF)) > 10) return true;
        }
        return false;
    }
}
