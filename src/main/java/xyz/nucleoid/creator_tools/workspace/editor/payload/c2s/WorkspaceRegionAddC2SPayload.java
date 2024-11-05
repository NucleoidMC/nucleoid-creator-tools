package xyz.nucleoid.creator_tools.workspace.editor.payload.c2s;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceRegionAddC2SPayload(String marker, BlockBounds bounds, NbtCompound data) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceRegionAddC2SPayload> ID = WorkspaceNetworking.id("workspace/region/add");

    public static final PacketCodec<PacketByteBuf, WorkspaceRegionAddC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, WorkspaceRegionAddC2SPayload::marker,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceRegionAddC2SPayload::bounds,
            PacketCodecs.UNLIMITED_NBT_COMPOUND, WorkspaceRegionAddC2SPayload::data,
            WorkspaceRegionAddC2SPayload::new
    );

    @Override
    public CustomPayload.Id<WorkspaceRegionAddC2SPayload> getId() {
        return ID;
    }
}
