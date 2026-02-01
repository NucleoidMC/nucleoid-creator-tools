package xyz.nucleoid.creator_tools.workspace.editor.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceRegionPayload(WorkspaceRegion region) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceRegionPayload> ID = WorkspaceNetworking.id("workspace/region");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceRegionPayload> CODEC = WorkspaceRegion.CODEC
            .map(WorkspaceRegionPayload::new, WorkspaceRegionPayload::region);

    @Override
    public CustomPacketPayload.Type<WorkspaceRegionPayload> type() {
        return ID;
    }
}
