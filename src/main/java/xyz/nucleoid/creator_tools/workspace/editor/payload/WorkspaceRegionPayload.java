package xyz.nucleoid.creator_tools.workspace.editor.payload;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceRegionPayload(WorkspaceRegion region) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceRegionPayload> ID = WorkspaceNetworking.id("workspace/region");

    public static final PacketCodec<PacketByteBuf, WorkspaceRegionPayload> CODEC = WorkspaceRegion.CODEC
            .xmap(WorkspaceRegionPayload::new, WorkspaceRegionPayload::region);

    @Override
    public CustomPayload.Id<WorkspaceRegionPayload> getId() {
        return ID;
    }
}
