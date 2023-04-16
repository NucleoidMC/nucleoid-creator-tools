package xyz.nucleoid.creator_tools.exporter;

import net.minecraft.data.DataProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.PathUtil;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;

public final class DumpExporter {
    private static final String DATA_DIRECTORY = "data";
    private static final String FILE_EXTENSION = ".json";

    private DumpExporter() {
    }

    public static CompletableFuture<Void> saveToExport(MinecraftServer server, NbtCompound nbt, Identifier identifier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var generatedPath = server.getSavePath(WorldSavePath.GENERATED);
                var path = getAndCheckDataPath(generatedPath, identifier, FILE_EXTENSION);

                var json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, nbt);
                System.out.println(json);

                Files.createDirectories(path.getParent());

                try (var output = Files.newOutputStream(path)) {
                    var jsonWriter = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));

                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent("  ");

                    JsonHelper.writeSorted(jsonWriter, json, DataProvider.JSON_KEY_SORTING_COMPARATOR);
                    jsonWriter.flush();
                }

                return null;
            } catch (IOException | InvalidIdentifierException e) {
                throw new CompletionException(e);
            }
        }, Util.getIoWorkerExecutor());
    }

    public static Path getDataPath(Path path, Identifier identifier, String extension) {
        try {
            Path namespacePath = path.resolve(identifier.getNamespace());
            Path dataPath = namespacePath.resolve(DATA_DIRECTORY);

            return PathUtil.getResourcePath(dataPath, identifier.getPath(), extension);
        } catch (InvalidPathException e) {
            throw new InvalidIdentifierException("Invalid resource path: " + identifier, e);
        }
    }

    private static Path getAndCheckDataPath(Path path, Identifier identifier, String extension) {
        if (identifier.getPath().contains("//")) {
            throw new InvalidIdentifierException("Invalid resource path: " + identifier);
        }

        Path dataPath = getDataPath(path, identifier, extension);
        if (!(dataPath.startsWith(path) && PathUtil.isNormal(dataPath) && PathUtil.isAllowedName(dataPath))) {
            throw new InvalidIdentifierException("Invalid resource path: " + dataPath);
        }

        return dataPath;
    }
}
