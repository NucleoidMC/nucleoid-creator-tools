package xyz.nucleoid.creator_tools.workspace.editor.payload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceDataPayload(Identifier workspaceId, CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceDataPayload> ID = WorkspaceNetworking.id("workspace/data");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceDataPayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WorkspaceDataPayload::workspaceId,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, WorkspaceDataPayload::data,
            WorkspaceDataPayload::new
    );

    @Override
    public CustomPacketPayload.Type<WorkspaceDataPayload> type() {
        return ID;
    }
}
