package xyz.nucleoid.creator_tools.workspace.editor.payload;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record WorkspaceDataPayload(Identifier workspaceId, NbtCompound data) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceDataPayload> ID = WorkspaceNetworking.id("workspace/data");

    public static final PacketCodec<PacketByteBuf, WorkspaceDataPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, WorkspaceDataPayload::workspaceId,
            PacketCodecs.UNLIMITED_NBT_COMPOUND, WorkspaceDataPayload::data,
            WorkspaceDataPayload::new
    );

    @Override
    public CustomPayload.Id<WorkspaceDataPayload> getId() {
        return ID;
    }
}
