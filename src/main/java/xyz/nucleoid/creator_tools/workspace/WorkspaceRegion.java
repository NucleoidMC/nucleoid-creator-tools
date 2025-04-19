package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceRegion(int runtimeId, String marker, BlockBounds bounds, NbtCompound data) {
    public static final PacketCodec<PacketByteBuf, WorkspaceRegion> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, WorkspaceRegion::runtimeId,
            PacketCodecs.STRING, WorkspaceRegion::marker,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceRegion::bounds,
            PacketCodecs.UNLIMITED_NBT_COMPOUND, WorkspaceRegion::data,
            WorkspaceRegion::new
    );

    public WorkspaceRegion withMarker(String marker) {
        return new WorkspaceRegion(this.runtimeId, marker, this.bounds, this.data);
    }

    public WorkspaceRegion withBounds(BlockBounds bounds) {
        return new WorkspaceRegion(this.runtimeId, this.marker, bounds, this.data);
    }

    public WorkspaceRegion withData(NbtCompound data) {
        return new WorkspaceRegion(this.runtimeId, this.marker, this.bounds, data);
    }

    public NbtCompound serialize(NbtCompound tag) {
        tag.putString("marker", this.marker);
        this.bounds.serialize(tag);
        tag.put("data", this.data);
        return tag;
    }

    public static WorkspaceRegion deserialize(int runtimeId, NbtCompound tag) {
        var marker = tag.getString("marker", "");
        var data = tag.getCompoundOrEmpty("data");
        return new WorkspaceRegion(runtimeId, marker, BlockBounds.deserialize(tag), data);
    }
}
