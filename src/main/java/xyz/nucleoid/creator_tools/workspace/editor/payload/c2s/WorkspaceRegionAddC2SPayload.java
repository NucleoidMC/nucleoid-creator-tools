package xyz.nucleoid.creator_tools.workspace.editor.payload.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceRegionAddC2SPayload(String marker, BlockBounds bounds, CompoundTag data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceRegionAddC2SPayload> ID = WorkspaceNetworking.id("workspace/region/add");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceRegionAddC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WorkspaceRegionAddC2SPayload::marker,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceRegionAddC2SPayload::bounds,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, WorkspaceRegionAddC2SPayload::data,
            WorkspaceRegionAddC2SPayload::new
    );

    @Override
    public CustomPacketPayload.Type<WorkspaceRegionAddC2SPayload> type() {
        return ID;
    }
}
