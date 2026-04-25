package xyz.nucleoid.creator_tools.workspace;

import com.google.common.io.Files;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceEditor;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceEditorManager;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;

import java.io.IOException;
import java.util.*;

public final class MapWorkspaceManager extends SavedData {
    private static final String VERY_OLD_KEY = "plasmid:map_workspaces";
    public static final String OLD_KEY = CreatorTools.ID + "_map_workspaces";
    public static final Identifier KEY = CreatorTools.identifier("map_workspaces");

    private static final BlockBounds DEFAULT_BOUNDS = BlockBounds.of(-16, 64, -16, 16, 96, 16);

    private final MinecraftServer server;

    private final Map<Identifier, MapWorkspace> workspacesById = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceKey<Level>, MapWorkspace> workspacesByDimension = new Reference2ObjectOpenHashMap<>();

    private final WorkspaceEditorManager editorManager;

    private MapWorkspaceManager(MinecraftServer server) {
        this.server = server;

        this.editorManager = new WorkspaceEditorManager();
    }

    public static MapWorkspaceManager get(MinecraftServer server) {
        var codec = CompoundTag.CODEC.xmap(nbt -> readNbt(server, nbt), manager -> {
            var nbt = new CompoundTag();
            manager.writeNbt(nbt);
            return nbt;
        });

        // Suppress the dataFixType being null - it can be null
        @SuppressWarnings("DataFlowIssue") var type = new SavedDataType<>(
                KEY,
                () -> new MapWorkspaceManager(server),
                codec,
                null
        );

        return server.getDataStorage().computeIfAbsent(type);
    }

    public void tick() {
        this.editorManager.tick();
    }

    @Nullable
    public WorkspaceEditor getEditorFor(ServerPlayer player) {
        return this.editorManager.getEditorFor(player);
    }

    public void onPlayerAddToWorld(ServerPlayer player, ServerLevel level) {
        this.editorManager.onPlayerAddToWorld(player, level);
    }

    public void onPlayerRemoveFromWorld(ServerPlayer player, ServerLevel level) {
        this.editorManager.onPlayerRemoveFromWorld(player, level);
    }

    public MapWorkspace open(Identifier identifier) {
        return this.open(identifier, this.createDefaultConfig());
    }

    public MapWorkspace open(Identifier identifier, RuntimeLevelConfig config) {
        var existingWorkspace = this.workspacesById.get(identifier);
        if (existingWorkspace != null) {
            return existingWorkspace;
        }

        var worldHandle = this.getOrCreateDimension(identifier, config);
        worldHandle.setTickWhenEmpty(false);

        var workspace = new MapWorkspace(worldHandle, identifier, DEFAULT_BOUNDS);
        this.workspacesById.put(identifier, workspace);
        this.workspacesByDimension.put(worldHandle.asLevel().dimension(), workspace);
        this.editorManager.addWorkspace(workspace);

        return workspace;
    }

    public boolean delete(MapWorkspace workspace) {
        if (this.workspacesById.remove(workspace.getIdentifier(), workspace)) {
            var world = workspace.getLevel();
            this.workspacesByDimension.remove(world.dimension());

            for (var player : new ArrayList<>(world.players())) {
                var returnPosition = WorkspaceTraveler.getLeaveReturn(player);
                if (returnPosition != null) {
                    returnPosition.applyTo(player);
                }
            }

            this.editorManager.removeWorkspace(workspace);

            workspace.getWorldHandle().delete();

            return true;
        }

        return false;
    }

    @Nullable
    public MapWorkspace byId(Identifier identifier) {
        return this.workspacesById.get(identifier);
    }

    @Nullable
    public MapWorkspace byDimension(ResourceKey<Level> dimension) {
        return this.workspacesByDimension.get(dimension);
    }

    public boolean isWorkspace(ResourceKey<Level> dimension) {
        return this.workspacesByDimension.containsKey(dimension);
    }

    public Set<Identifier> getWorkspaceIds() {
        return this.workspacesById.keySet();
    }

    public Collection<MapWorkspace> getWorkspaces() {
        return this.workspacesById.values();
    }

    private static MapWorkspaceManager readNbt(MinecraftServer server, CompoundTag nbt) {
        var manager = new MapWorkspaceManager(server);

        for (var key : nbt.keySet()) {
            var identifier = Identifier.tryParse(key);

            if (identifier != null) {
                var root = nbt.getCompoundOrEmpty(key);

                var worldHandle = manager.getOrCreateDimension(identifier, manager.createDefaultConfig());
                worldHandle.setTickWhenEmpty(false);

                var workspace = MapWorkspace.deserialize(worldHandle, root);
                manager.workspacesById.put(identifier, workspace);
                manager.workspacesByDimension.put(worldHandle.asLevel().dimension(), workspace);
                manager.editorManager.addWorkspace(workspace);
            }
        }

        return manager;
    }

    public CompoundTag writeNbt(CompoundTag nbt) {
        for (var entry : this.workspacesById.entrySet()) {
            String key = entry.getKey().toString();
            nbt.put(key, entry.getValue().serialize(new CompoundTag()));
        }
        return nbt;
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    private RuntimeLevelHandle getOrCreateDimension(Identifier identifier, RuntimeLevelConfig config) {

        var dimensionId = identifier.withPrefix("workspace_");
        var fantasyWorld = Fantasy.get(this.server).getOrOpenPersistentLevel(dimensionId, config);
        this.applyDefaultsToConfig(config, fantasyWorld.asLevel());
        return fantasyWorld;
    }

    private void applyDefaultsToConfig(RuntimeLevelConfig config, ServerLevel level) {
        // TODO: fantasy: make all commands channel through the correct world
        //        + then serialize the RuntimeLevelConfig for each workspace
        config.setDifficulty(this.server.overworld().getDifficulty());
        var serverRules = level.getGameRules();
        var workspaceRules = config.getGameRules();

        serverRules.visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public void visitInteger(@NonNull GameRule<Integer> key) {
                var value = serverRules.get(key);
                if (!workspaceRules.contains(key)) {
                    workspaceRules.set(key, value);
                }
            }

            @Override
            public void visitBoolean(@NonNull GameRule<Boolean> key) {
                var value = serverRules.get(key);
                if (!workspaceRules.contains(key)) {
                    workspaceRules.set(key, value);
                }
            }
        });
    }

    private RuntimeLevelConfig createDefaultConfig() {
        var registries = this.server.registryAccess();
        var generator = new VoidChunkGenerator(registries.lookupOrThrow(Registries.BIOME));

        return new RuntimeLevelConfig()
                .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                .setGenerator(generator);
    }

    /**
     * Migrates the file storing map workspaces to a path that doesn't contain a colon.
     * This fixes an issue on Windows where saving map workspaces would always fail.
     */
    public static void migratePath(MinecraftServer server) {
        // Do not attempt migration on Windows, as even trying to resolve the broken path will crash
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }

        var manager = server.getDataStorage();

        try {
            // Don't overwrite a migrated file, if one exists
            var path = manager.getDataFile(MapWorkspaceManager.KEY);
            var newFile = path.toFile();
            if (newFile.isFile()) return;

            // Yes a hack, but avoids an access widener
            var dataFolder = manager.getDataFile(MapWorkspaceManager.KEY)
                    .getParent() // world/data/nucleoid_creator_tools
                    .getParent(); // world/data

            var oldPath = dataFolder.resolve(OLD_KEY + ".dat");
            var oldFile = oldPath.toFile();
            if (oldFile.isFile()) {
                Files.createParentDirs(newFile);
                Files.move(oldFile, newFile);
                CreatorTools.LOGGER.warn("Migrated map workspaces from old path '{}' to '{}'", oldFile, newFile);
                return;
            }

            var veryOldPath = dataFolder.resolve(MapWorkspaceManager.VERY_OLD_KEY + ".dat"); // world/data/plasmid:map_workspaces
            var veryOldFile = veryOldPath.toFile();
            if (!veryOldFile.isFile()) return;

            Files.createParentDirs(newFile);
            Files.move(veryOldFile, newFile);
            CreatorTools.LOGGER.warn("Migrated map workspaces from very old path '{}' to '{}'", veryOldFile, newFile);

        } catch (IOException e) {
            CreatorTools.LOGGER.warn("Failed to migrate map workspaces from legacy path", e);
        }
    }
}
