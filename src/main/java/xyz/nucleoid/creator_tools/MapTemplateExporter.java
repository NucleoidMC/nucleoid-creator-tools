package xyz.nucleoid.creator_tools;

import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MapTemplateExporter {
    private static final Path ROOT = Paths.get(CreatorTools.ID, "export");

    private MapTemplateExporter() {
    }

    public static MapTemplate loadFromExport(Identifier location) throws IOException {
        var path = getExportPathFor(location);
        if (!Files.exists(path)) {
            throw new IOException("Export does not exist for " + location + "!");
        }

        try (var input = Files.newInputStream(path)) {
            return MapTemplateSerializer.loadFrom(input);
        }
    }

    public static CompletableFuture<Void> saveToExport(MapTemplate template, Identifier identifier) {
        return CompletableFuture.supplyAsync(() -> {
            var path = getExportPathFor(identifier);
            try {
                Files.createDirectories(path.getParent());
                try (var output = Files.newOutputStream(path)) {
                    MapTemplateSerializer.saveTo(template, output);
                    return null;
                }
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.getIoWorkerExecutor());
    }

    private static Path getExportPathFor(Identifier identifier) {
        identifier = MapTemplateSerializer.getResourcePathFor(identifier);
        return ROOT.resolve(identifier.getNamespace()).resolve(identifier.getPath());
    }
}
