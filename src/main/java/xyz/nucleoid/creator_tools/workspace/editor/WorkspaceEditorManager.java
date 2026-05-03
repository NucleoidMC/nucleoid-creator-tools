package xyz.nucleoid.creator_tools.workspace.editor;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.WorkspaceListener;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Map;
import java.util.UUID;

public final class WorkspaceEditorManager {
    private final Map<ResourceKey<Level>, WorkspaceHandler> workspaces = new Reference2ObjectOpenHashMap<>();

    public void onPlayerAddToWorld(ServerPlayer player, ServerLevel level) {
        var workspace = this.workspaces.get(level.dimension());
        if (workspace != null) {
            workspace.addEditor(player, this.createEditorFor(player, workspace.workspace));
        }
    }

    public void onPlayerRemoveFromWorld(ServerPlayer player, ServerLevel level) {
        var workspace = this.workspaces.get(level.dimension());
        if (workspace != null) {
            var editor = workspace.editors.remove(player.getUUID());
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

        this.workspaces.put(workspace.getLevel().dimension(), handler);
    }

    public void removeWorkspace(MapWorkspace workspace) {
        this.workspaces.remove(workspace.getLevel().dimension());
    }

    private WorkspaceEditor createEditorFor(ServerPlayer player, MapWorkspace workspace) {
        int protocolVersion = WorkspaceTraveler.getCreatorToolsProtocolVersion(player);
        return protocolVersion == 1 ? new NetworkedWorkspaceEditor(player, workspace) : new ServersideWorkspaceEditor(player, workspace);
    }

    @Nullable
    public WorkspaceEditor getEditorFor(ServerPlayer player) {
        var workspace = this.workspaces.get(player.level().dimension());
        if (workspace != null) {
            return workspace.editors.get(player.getUUID());
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

        void addEditor(ServerPlayer player, WorkspaceEditor editor) {
            this.editors.put(player.getUUID(), editor);

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
        public void onSetData(CompoundTag data) {
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
