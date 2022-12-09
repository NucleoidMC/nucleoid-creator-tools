package xyz.nucleoid.creator_tools.workspace.editor;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.WorkspaceListener;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Map;
import java.util.UUID;

public final class WorkspaceEditorManager {
    private final Map<RegistryKey<World>, WorkspaceHandler> workspaces = new Reference2ObjectOpenHashMap<>();

    public void onPlayerAddToWorld(ServerPlayerEntity player, ServerWorld world) {
        var workspace = this.workspaces.get(world.getRegistryKey());
        if (workspace != null) {
            workspace.addEditor(player, this.createEditorFor(player, workspace.workspace));
        }
    }

    public void onPlayerRemoveFromWorld(ServerPlayerEntity player, ServerWorld world) {
        var workspace = this.workspaces.get(world.getRegistryKey());
        if (workspace != null) {
            var editor = workspace.editors.remove(player.getUuid());
            if (editor != null) {
                editor.onLeave();
            }
        }
    }

    public void tick() {
        for (var workspace : this.workspaces.values()) {
            workspace.tick();
        }
    }

    public void addWorkspace(MapWorkspace workspace) {
        var handler = new WorkspaceHandler(workspace);
        workspace.addListener(handler);

        this.workspaces.put(workspace.getWorld().getRegistryKey(), handler);
    }

    public void removeWorkspace(MapWorkspace workspace) {
        this.workspaces.remove(workspace.getWorld().getRegistryKey());
    }

    private WorkspaceEditor createEditorFor(ServerPlayerEntity player, MapWorkspace workspace) {
        int protocolVersion = WorkspaceTraveler.getCreatorToolsProtocolVersion(player);
        return protocolVersion == 1 ? new NetworkedWorkspaceEditor(player, workspace) : new ServersideWorkspaceEditor(player, workspace);
    }

    @Nullable
    public WorkspaceEditor getEditorFor(ServerPlayerEntity player) {
        var workspace = this.workspaces.get(player.getWorld().getRegistryKey());
        if (workspace != null) {
            return workspace.editors.get(player.getUuid());
        } else {
            return null;
        }
    }

    private static class WorkspaceHandler implements WorkspaceListener {
        final MapWorkspace workspace;
        final Map<UUID, WorkspaceEditor> editors = new Object2ObjectOpenHashMap<>();

        WorkspaceHandler(MapWorkspace workspace) {
            this.workspace = workspace;
        }

        void addEditor(ServerPlayerEntity player, WorkspaceEditor editor) {
            this.editors.put(player.getUuid(), editor);

            editor.onEnter();

            editor.setOrigin(this.workspace.getOrigin());
            editor.setBounds(this.workspace.getBounds());

            for (var region : this.workspace.getRegions()) {
                editor.addRegion(region);
            }
        }

        void tick() {
            for (var editor : this.editors.values()) {
                editor.tick();
            }
        }

        @Override
        public void onSetBounds(BlockBounds bounds) {
            for (var editor : this.editors.values()) {
                editor.setBounds(bounds);
            }
        }

        @Override
        public void onSetOrigin(BlockPos origin) {
            for (var editor : this.editors.values()) {
                editor.setOrigin(origin);
            }
        }

        @Override
        public void onSetData(NbtCompound data) {
            for (var editor : this.editors.values()) {
                editor.setData(data);
            }
        }

        @Override
        public void onAddRegion(WorkspaceRegion region) {
            for (var editor : this.editors.values()) {
                editor.addRegion(region);
            }
        }

        @Override
        public void onRemoveRegion(WorkspaceRegion region) {
            for (var editor : this.editors.values()) {
                editor.removeRegion(region);
            }
        }

        @Override
        public void onUpdateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
            for (var editor : this.editors.values()) {
                editor.updateRegion(lastRegion, newRegion);
            }
        }
    }
}
