package xyz.nucleoid.creator_tools.workspace.editor.payload;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceBoundsPayload(Identifier workspaceId, BlockBounds bounds) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceBoundsPayload> ID = WorkspaceNetworking.id("workspace/bounds");

    public static final PacketCodec<PacketByteBuf, WorkspaceBoundsPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, WorkspaceBoundsPayload::workspaceId,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceBoundsPayload::bounds,
            WorkspaceBoundsPayload::new
    );

    @Override
    public CustomPayload.Id<WorkspaceBoundsPayload> getId() {
        return ID;
    }
}
