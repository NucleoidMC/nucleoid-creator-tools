package xyz.nucleoid.creator_tools.workspace;

import com.google.common.io.Files;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceEditor;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceEditorManager;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MapWorkspaceManager extends PersistentState {
    private static final String LEGACY_KEY = "plasmid:map_workspaces";
    public static final String KEY = CreatorTools.ID + "_map_workspaces";

    private static final BlockBounds DEFAULT_BOUNDS = BlockBounds.of(-16, 64, -16, 16, 96, 16);

    private final MinecraftServer server;

    private final Map<Identifier, MapWorkspace> workspacesById = new Object2ObjectOpenHashMap<>();
    private final Map<RegistryKey<World>, MapWorkspace> workspacesByDimension = new Reference2ObjectOpenHashMap<>();

    private final WorkspaceEditorManager editorManager;

    private MapWorkspaceManager(MinecraftServer server) {
        this.server = server;

        this.editorManager = new WorkspaceEditorManager();
    }

    public static MapWorkspaceManager get(MinecraftServer server) {
        var codec = NbtCompound.CODEC.xmap(nbt -> {
            return readNbt(server, nbt);
        }, manager -> {
            var nbt = new NbtCompound();
            manager.writeNbt(nbt);
            return nbt;
        });

        var type = new PersistentStateType<>(
                KEY,
                () -> new MapWorkspaceManager(server),
                codec,
                null
        );

        return server.getOverworld().getPersistentStateManager().getOrCreate(type);
    }

    public void tick() {
        this.editorManager.tick();
    }

    @Nullable
    public WorkspaceEditor getEditorFor(ServerPlayerEntity player) {
        return this.editorManager.getEditorFor(player);
    }

    public void onPlayerAddToWorld(ServerPlayerEntity player, ServerWorld world) {
        this.editorManager.onPlayerAddToWorld(player, world);
    }

    public void onPlayerRemoveFromWorld(ServerPlayerEntity player, ServerWorld world) {
        this.editorManager.onPlayerRemoveFromWorld(player, world);
    }

    public MapWorkspace open(Identifier identifier) {
        return this.open(identifier, this.createDefaultConfig());
    }

    public MapWorkspace open(Identifier identifier, RuntimeWorldConfig config) {
        var existingWorkspace = this.workspacesById.get(identifier);
        if (existingWorkspace != null) {
            return existingWorkspace;
        }

        var worldHandle = this.getOrCreateDimension(identifier, config);
        worldHandle.setTickWhenEmpty(false);

        var workspace = new MapWorkspace(worldHandle, identifier, DEFAULT_BOUNDS);
        this.workspacesById.put(identifier, workspace);
        this.workspacesByDimension.put(worldHandle.asWorld().getRegistryKey(), workspace);
        this.editorManager.addWorkspace(workspace);

        return workspace;
    }

    public boolean delete(MapWorkspace workspace) {
        if (this.workspacesById.remove(workspace.getIdentifier(), workspace)) {
            var world = workspace.getWorld();
            this.workspacesByDimension.remove(world.getRegistryKey());

            for (var player : new ArrayList<>(world.getPlayers())) {
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
    public MapWorkspace byDimension(RegistryKey<World> dimension) {
        return this.workspacesByDimension.get(dimension);
    }

    public boolean isWorkspace(RegistryKey<World> dimension) {
        return this.workspacesByDimension.containsKey(dimension);
    }

    public Set<Identifier> getWorkspaceIds() {
        return this.workspacesById.keySet();
    }

    public Collection<MapWorkspace> getWorkspaces() {
        return this.workspacesById.values();
    }

    private static MapWorkspaceManager readNbt(MinecraftServer server, NbtCompound nbt) {
        var manager = new MapWorkspaceManager(server);

        for (var key : nbt.getKeys()) {
            var identifier = Identifier.tryParse(key);

            if (identifier != null) {
                var root = nbt.getCompoundOrEmpty(key);

                var worldHandle = manager.getOrCreateDimension(identifier, manager.createDefaultConfig());
                worldHandle.setTickWhenEmpty(false);

                var workspace = MapWorkspace.deserialize(worldHandle, root);
                manager.workspacesById.put(identifier, workspace);
                manager.workspacesByDimension.put(worldHandle.asWorld().getRegistryKey(), workspace);
                manager.editorManager.addWorkspace(workspace);
            }
        }

        return manager;
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        for (var entry : this.workspacesById.entrySet()) {
            String key = entry.getKey().toString();
            nbt.put(key, entry.getValue().serialize(new NbtCompound()));
        }
        return nbt;
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    private RuntimeWorldHandle getOrCreateDimension(Identifier identifier, RuntimeWorldConfig config) {
        this.applyDefaultsToConfig(config);

        var dimensionId = identifier.withPrefixedPath("workspace_");
        return Fantasy.get(this.server).getOrOpenPersistentWorld(dimensionId, config);
    }

    private void applyDefaultsToConfig(RuntimeWorldConfig config) {
        // TODO: fantasy: make all commands channel through the correct world
        //        + then serialize the runtimeworldconfig for each workspace
        config.setDifficulty(this.server.getOverworld().getDifficulty());

        var serverRules = MapWorkspaceManager.this.server.getGameRules();
        var workspaceRules = config.getGameRules();

        serverRules.accept(new GameRules.Visitor() {
            @Override
            public void visitInt(GameRules.Key<GameRules.IntRule> key, GameRules.Type<GameRules.IntRule> type) {
                var value = serverRules.get(key);
                if (!workspaceRules.contains(key)) {
                    workspaceRules.set(key, value.get());
                }
            }

            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                var value = serverRules.get(key);
                if (!workspaceRules.contains(key)) {
                    workspaceRules.set(key, value.get());
                }
            }
        });
    }

    private RuntimeWorldConfig createDefaultConfig() {
        var registries = this.server.getRegistryManager();
        var generator = new VoidChunkGenerator(registries.getOrThrow(RegistryKeys.BIOME));

        return new RuntimeWorldConfig()
                .setDimensionType(DimensionTypes.OVERWORLD)
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

        var manager = server.getOverworld().getPersistentStateManager();

        try {
            // Don't overwrite a migrated file, if one exists
            var path = manager.getFile(MapWorkspaceManager.KEY);
            var file = path.toFile();
            if (file.isFile()) return;

            var legacyPath = manager.getFile(MapWorkspaceManager.LEGACY_KEY);
            var legacyFile = legacyPath.toFile();
            if (!legacyFile.isFile()) return;

            Files.move(legacyFile, file);
            CreatorTools.LOGGER.warn("Migrated map workspaces from legacy path '{}' to '{}'", legacyFile, file);
        } catch (IOException e) {
            CreatorTools.LOGGER.warn("Failed to migrate map workspaces from legacy path 'data/plasmid:map_workspaces.nbt'", e);
        }
    }
}
