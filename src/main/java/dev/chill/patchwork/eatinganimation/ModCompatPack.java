package dev.chill.patchwork.eatinganimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.chill.patchwork.Patchwork;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Eating Animation's generated compatibility pack for one other mod (Java port of its ModCompatResourcePack): for
 * every food folder under {@code eatinganimation_compat/<modid>/} it serves an item model with the eating overrides
 * plus the three animation frames.
 */
final class ModCompatPack extends AbstractPackResources {
    private static final String NAMESPACE = "eatinganimation";
    private static final int FRAMES = 3;

    private final String modId;
    private final Set<String> namespaces;
    private final Map<ResourceLocation, IoSupplier<InputStream>> resources = new HashMap<>();
    private final byte[] packMcMeta;

    ModCompatPack(PackLocationInfo location, Path modFolder, String modId) {
        super(location);
        this.modId = modId;
        this.namespaces = Set.of(NAMESPACE, modId);
        try (Stream<Path> items = Files.list(modFolder)) {
            for (Path itemFolder : items.filter(Files::isDirectory).toList()) {
                addItem(itemFolder);
            }
        } catch (IOException e) {
            Patchwork.LOG.error("[Patchwork/eatinganimation] Could not read compat data for {}", modId, e);
        }
        this.packMcMeta = "{\"pack\":{\"description\":\"EatingAnimations %s compatibility\",\"pack_format\":%s}}"
                .formatted(modId, SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES))
                .getBytes(StandardCharsets.UTF_8);
    }

    private void addItem(Path itemFolder) {
        String name = itemFolder.getFileName().toString().replace("/", "");
        Properties props = readProperties(itemFolder.resolve("properties.json"));
        String itemModel = itemModel(name, props.defaultModel.orElse(modId + ":item/" + name), props.values);
        resources.put(ResourceLocation.fromNamespaceAndPath(modId, "models/item/" + name + ".json"), () -> stream(itemModel));
        for (int i = 0; i < FRAMES; i++) {
            Path frame = itemFolder.resolve(i + ".png");
            resources.put(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/item/" + modId + "/" + name + "_" + i + ".png"),
                    () -> Files.newInputStream(frame));
            String frameModel = "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"%s:item/%s/%s_%s\"}}"
                    .formatted(NAMESPACE, modId, name, i);
            resources.put(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "models/item/" + modId + "/" + name + "_" + i + ".json"),
                    () -> stream(frameModel));
        }
    }

    private String itemModel(String name, String normalTexture, List<Float> values) {
        return ("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"%1$s\"},\"overrides\":["
                + "{\"predicate\":{\"%2$s:eating\":1,\"%2$s:eat\":%5$s},\"model\":\"%2$s:item/%3$s/%4$s_0\"},"
                + "{\"predicate\":{\"%2$s:eating\":1,\"%2$s:eat\":%6$s},\"model\":\"%2$s:item/%3$s/%4$s_1\"},"
                + "{\"predicate\":{\"%2$s:eating\":1,\"%2$s:eat\":%7$s},\"model\":\"%2$s:item/%3$s/%4$s_2\"}]}")
                .formatted(normalTexture, NAMESPACE, modId, name, values.get(0), values.get(1), values.get(2));
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Properties readProperties(Path file) {
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return Properties.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            } catch (Exception e) {
                Patchwork.LOG.error("[Patchwork/eatinganimation] Bad properties file {}", file, e);
            }
        }
        return new Properties(Optional.empty(), Properties.DEFAULT_VALUES);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespaces.contains(namespace)) {
            return;
        }
        resources.forEach((location, supplier) -> {
            if (location.getNamespace().equals(namespace) && location.getPath().startsWith(path)) {
                output.accept(location, supplier);
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? namespaces : Set.of();
    }

    @Override
    public void close() {}

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        if (paths.length == 1 && paths[0].equals("pack.mcmeta")) {
            return () -> new ByteArrayInputStream(packMcMeta);
        }
        return resources.get(ResourceLocation.fromNamespaceAndPath(modId, String.join("/", paths)));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        return type == PackType.CLIENT_RESOURCES ? resources.get(location) : null;
    }

    private record Properties(Optional<String> defaultModel, List<Float> values) {
        static final List<Float> DEFAULT_VALUES = List.of(0.35F, 0.7F, 0.9F);
        static final Codec<Properties> CODEC = RecordCodecBuilder.create(in -> in.group(
                Codec.STRING.optionalFieldOf("layer0").forGetter(Properties::defaultModel),
                Codec.FLOAT.listOf().optionalFieldOf("values", DEFAULT_VALUES).forGetter(Properties::values)
        ).apply(in, Properties::new));
    }
}
