package xyz.nucleoid.creator_tools.workspace.editor;

import java.util.stream.Collectors;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * An editor implementation that uses {@linkplain WorkspaceNetworking networking} for use with clientside mods.
 */
public class NetworkedWorkspaceEditor implements WorkspaceEditor {
    private final ServerPlayerEntity player;
    private final MapWorkspace workspace;

    public NetworkedWorkspaceEditor(ServerPlayerEntity player, MapWorkspace workspace) {
        this.player = player;
        this.workspace = workspace;
    }

    @Override
    public void onEnter() {
        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_ENTER_ID)) {
            var buf = PacketByteBufs.create();

            buf.writeIdentifier(this.workspace.getIdentifier());
            WorkspaceNetworking.writeBounds(buf, this.workspace.getBounds());
            buf.writeIdentifier(this.workspace.getWorld().getRegistryKey().getValue());
            buf.writeNbt(this.workspace.getData());

            this.sendPacket(WorkspaceNetworking.WORKSPACE_ENTER_ID, buf);
        }

        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_REGIONS_ID)) {
            var groups = this.workspace.getRegions().stream().collect(Collectors.groupingBy(WorkspaceRegion::marker));

            for (var entry : groups.entrySet()) {
                var buf = PacketByteBufs.create();

                buf.writeString(entry.getKey());

                for (var region : entry.getValue()) {
                    buf.writeInt(region.runtimeId());
                    WorkspaceNetworking.writeBounds(buf, region.bounds());
                    buf.writeNbt(region.data());
                }

                this.sendPacket(WorkspaceNetworking.WORKSPACE_REGIONS_ID, buf);
            }
        }
    }

    @Override
    public void onLeave() {
        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_LEAVE_ID)) {
            var buf = PacketByteBufs.create();
            buf.writeIdentifier(this.workspace.getIdentifier());
            this.sendPacket(WorkspaceNetworking.WORKSPACE_LEAVE_ID, buf);
        }
    }

    @Override
    public void addRegion(WorkspaceRegion region) {
        this.sendRegionPacket(region);
    }

    @Override
    public void removeRegion(WorkspaceRegion region) {
        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_REGION_REMOVE_ID)) {
            var buf = PacketByteBufs.create();
            buf.writeInt(region.runtimeId());
            this.sendPacket(WorkspaceNetworking.WORKSPACE_REGION_REMOVE_ID, buf);
        }
    }

    @Override
    public void updateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
        this.sendRegionPacket(newRegion);
    }

    @Override
    public void setBounds(BlockBounds bounds) {
        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_BOUNDS_ID)) {
            var buf = PacketByteBufs.create();

            buf.writeIdentifier(this.workspace.getIdentifier());
            WorkspaceNetworking.writeBounds(buf, bounds);

            this.sendPacket(WorkspaceNetworking.WORKSPACE_BOUNDS_ID, buf);
        }
    }

    @Override
    public void setData(NbtCompound data) {
        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_DATA_ID)) {
            var buf = PacketByteBufs.create();

            buf.writeIdentifier(this.workspace.getIdentifier());
            buf.writeNbt(data);

            this.sendPacket(WorkspaceNetworking.WORKSPACE_DATA_ID, buf);
        }
    }

    private boolean canSendPacket(Identifier channel) {
        return ServerPlayNetworking.canSend(this.player, channel);
    }

    private void sendPacket(Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(this.player, channel, buf);
    }

    private boolean sendRegionPacket(WorkspaceRegion region) {
        if (this.canSendPacket(WorkspaceNetworking.WORKSPACE_REGION_ID)) {
            var buf = PacketByteBufs.create();

            buf.writeInt(region.runtimeId());
            WorkspaceNetworking.writeRegion(buf, region);

            this.sendPacket(WorkspaceNetworking.WORKSPACE_REGION_ID, buf);
            return true;
        }

        return false;
    }
}
