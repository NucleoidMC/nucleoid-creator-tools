package xyz.nucleoid.creator_tools.workspace.editor.payload.s2c;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceEnterS2CPayload(Identifier workspaceId, BlockBounds bounds, Identifier worldId, NbtCompound data) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceEnterS2CPayload> ID = WorkspaceNetworking.id("workspace/enter");

    public static final PacketCodec<PacketByteBuf, WorkspaceEnterS2CPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, WorkspaceEnterS2CPayload::workspaceId,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceEnterS2CPayload::bounds,
            Identifier.PACKET_CODEC, WorkspaceEnterS2CPayload::worldId,
            PacketCodecs.UNLIMITED_NBT_COMPOUND, WorkspaceEnterS2CPayload::data,
            WorkspaceEnterS2CPayload::new
    );

    @Override
    public CustomPayload.Id<WorkspaceEnterS2CPayload> getId() {
        return ID;
    }
}
