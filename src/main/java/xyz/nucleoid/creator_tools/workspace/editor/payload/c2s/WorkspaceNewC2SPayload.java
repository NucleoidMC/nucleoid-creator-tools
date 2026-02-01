package xyz.nucleoid.creator_tools.workspace.editor.payload.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceNewC2SPayload(Identifier workspaceId, BlockBounds bounds, String generator, CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceNewC2SPayload> ID = WorkspaceNetworking.id("workspace/new");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceNewC2SPayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WorkspaceNewC2SPayload::workspaceId,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceNewC2SPayload::bounds,
            ByteBufCodecs.STRING_UTF8, WorkspaceNewC2SPayload::generator,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, WorkspaceNewC2SPayload::data,
            WorkspaceNewC2SPayload::new
    );

    @Override
    public CustomPacketPayload.Type<WorkspaceNewC2SPayload> type() {
        return ID;
    }
}
