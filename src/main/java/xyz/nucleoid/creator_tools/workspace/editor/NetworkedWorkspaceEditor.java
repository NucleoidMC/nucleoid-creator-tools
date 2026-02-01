package xyz.nucleoid.creator_tools.workspace.editor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.editor.payload.*;
import xyz.nucleoid.creator_tools.workspace.editor.payload.s2c.WorkspaceEnterS2CPayload;
import xyz.nucleoid.creator_tools.workspace.editor.payload.s2c.WorkspaceRegionsS2CPayload;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.stream.Collectors;

/**
 * An editor implementation that uses {@linkplain WorkspaceNetworking networking} for use with clientside mods.
 */
public class NetworkedWorkspaceEditor implements WorkspaceEditor {
    private final ServerPlayer player;
    private final MapWorkspace workspace;

    public NetworkedWorkspaceEditor(ServerPlayer player, MapWorkspace workspace) {
        this.player = player;
        this.workspace = workspace;
    }

    @Override
    public void onEnter() {
        if (this.canSendPacket(WorkspaceEnterS2CPayload.ID)) {
            this.sendPacket(new WorkspaceEnterS2CPayload(
                    this.workspace.getIdentifier(),
                    this.workspace.getBounds(),
                    this.workspace.getLevel().dimension().identifier(),
                    this.workspace.getData()));
        }

        if (this.canSendPacket(WorkspaceRegionsS2CPayload.ID)) {
            var groups = this.workspace.getRegions().stream()
                    .collect(Collectors.groupingBy(WorkspaceRegion::marker,
                            Collectors.mapping(WorkspaceRegionsS2CPayload.Entry::fromRegion, Collectors.toList())));

            for (var entry : groups.entrySet()) {
                this.sendPacket(new WorkspaceRegionsS2CPayload(entry.getKey(), entry.getValue()));
            }
        }
    }

    @Override
    public void onLeave() {
        if (this.canSendPacket(WorkspaceLeavePayload.ID)) {
            this.sendPacket(new WorkspaceLeavePayload(this.workspace.getIdentifier()));
        }
    }

    @Override
    public void addRegion(WorkspaceRegion region) {
        this.sendRegionPacket(region);
    }

    @Override
    public void removeRegion(WorkspaceRegion region) {
        if (this.canSendPacket(WorkspaceRegionRemovePayload.ID)) {
            this.sendPacket(new WorkspaceRegionRemovePayload(region.runtimeId()));
        }
    }

    @Override
    public void updateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
        this.sendRegionPacket(newRegion);
    }

    @Override
    public void setBounds(BlockBounds bounds) {
        if (this.canSendPacket(WorkspaceBoundsPayload.ID)) {
            this.sendPacket(new WorkspaceBoundsPayload(this.workspace.getIdentifier(), bounds));
        }
    }

    @Override
    public void setData(CompoundTag data) {
        if (this.canSendPacket(WorkspaceDataPayload.ID)) {
            this.sendPacket(new WorkspaceDataPayload(this.workspace.getIdentifier(), data));
        }
    }

    private boolean canSendPacket(CustomPacketPayload.Type<?> channel) {
        return ServerPlayNetworking.canSend(this.player, channel);
    }

    private void sendPacket(CustomPacketPayload payload) {
        ServerPlayNetworking.send(this.player, payload);
    }

    private boolean sendRegionPacket(WorkspaceRegion region) {
        if (this.canSendPacket(WorkspaceRegionPayload.ID)) {
            this.sendPacket(new WorkspaceRegionPayload(region));
            return true;
        }

        return false;
    }
}
