package xyz.nucleoid.creator_tools.workspace.editor.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceRegionRemovePayload(int regionId) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceRegionRemovePayload> ID = WorkspaceNetworking.id("workspace/region/remove");

    public static final PacketCodec<ByteBuf, WorkspaceRegionRemovePayload> CODEC = PacketCodecs.VAR_INT
            .xmap(WorkspaceRegionRemovePayload::new, WorkspaceRegionRemovePayload::regionId);

    @Override
    public CustomPayload.Id<WorkspaceRegionRemovePayload> getId() {
        return ID;
    }
}
