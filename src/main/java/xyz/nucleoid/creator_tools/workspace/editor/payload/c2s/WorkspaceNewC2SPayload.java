package xyz.nucleoid.creator_tools.workspace.editor.payload.c2s;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceNewC2SPayload(Identifier workspaceId, BlockBounds bounds, String generator, NbtCompound data) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceNewC2SPayload> ID = WorkspaceNetworking.id("workspace/new");

    public static final PacketCodec<PacketByteBuf, WorkspaceNewC2SPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, WorkspaceNewC2SPayload::workspaceId,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceNewC2SPayload::bounds,
            PacketCodecs.STRING, WorkspaceNewC2SPayload::generator,
            PacketCodecs.UNLIMITED_NBT_COMPOUND, WorkspaceNewC2SPayload::data,
            WorkspaceNewC2SPayload::new
    );

    @Override
    public CustomPayload.Id<WorkspaceNewC2SPayload> getId() {
        return ID;
    }
}
