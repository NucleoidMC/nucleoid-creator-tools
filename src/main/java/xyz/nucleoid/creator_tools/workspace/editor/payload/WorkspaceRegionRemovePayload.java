package xyz.nucleoid.creator_tools.workspace.editor.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceRegionRemovePayload(int regionId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceRegionRemovePayload> ID = WorkspaceNetworking.id("workspace/region/remove");

    public static final StreamCodec<ByteBuf, WorkspaceRegionRemovePayload> CODEC = ByteBufCodecs.VAR_INT
            .map(WorkspaceRegionRemovePayload::new, WorkspaceRegionRemovePayload::regionId);

    @Override
    public CustomPacketPayload.Type<WorkspaceRegionRemovePayload> type() {
        return ID;
    }
}
