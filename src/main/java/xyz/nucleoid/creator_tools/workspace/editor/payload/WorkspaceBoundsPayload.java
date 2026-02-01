package xyz.nucleoid.creator_tools.workspace.editor.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceBoundsPayload(Identifier workspaceId, BlockBounds bounds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceBoundsPayload> ID = WorkspaceNetworking.id("workspace/bounds");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceBoundsPayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WorkspaceBoundsPayload::workspaceId,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceBoundsPayload::bounds,
            WorkspaceBoundsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<WorkspaceBoundsPayload> type() {
        return ID;
    }
}
