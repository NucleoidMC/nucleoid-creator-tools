package xyz.nucleoid.creator_tools.workspace.editor.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceLeavePayload(Identifier workspaceId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceLeavePayload> ID = WorkspaceNetworking.id("workspace/leave");

    public static final StreamCodec<ByteBuf, WorkspaceLeavePayload> CODEC = Identifier.STREAM_CODEC
            .map(WorkspaceLeavePayload::new, WorkspaceLeavePayload::workspaceId);

    @Override
    public CustomPacketPayload.Type<WorkspaceLeavePayload> type() {
        return ID;
    }
}
