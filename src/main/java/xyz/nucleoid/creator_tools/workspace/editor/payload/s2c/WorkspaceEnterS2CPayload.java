package xyz.nucleoid.creator_tools.workspace.editor.payload.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceEnterS2CPayload(Identifier workspaceId, BlockBounds bounds, Identifier worldId, CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceEnterS2CPayload> ID = WorkspaceNetworking.id("workspace/enter");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceEnterS2CPayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WorkspaceEnterS2CPayload::workspaceId,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceEnterS2CPayload::bounds,
            Identifier.STREAM_CODEC, WorkspaceEnterS2CPayload::worldId,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, WorkspaceEnterS2CPayload::data,
            WorkspaceEnterS2CPayload::new
    );

    @Override
    public CustomPacketPayload.Type<WorkspaceEnterS2CPayload> type() {
        return ID;
    }
}
