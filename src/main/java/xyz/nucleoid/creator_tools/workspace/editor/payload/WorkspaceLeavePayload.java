package xyz.nucleoid.creator_tools.workspace.editor.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceLeavePayload(Identifier workspaceId) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceLeavePayload> ID = WorkspaceNetworking.id("workspace/leave");

    public static final PacketCodec<ByteBuf, WorkspaceLeavePayload> CODEC = Identifier.PACKET_CODEC
            .xmap(WorkspaceLeavePayload::new, WorkspaceLeavePayload::workspaceId);

    @Override
    public CustomPayload.Id<WorkspaceLeavePayload> getId() {
        return ID;
    }
}
