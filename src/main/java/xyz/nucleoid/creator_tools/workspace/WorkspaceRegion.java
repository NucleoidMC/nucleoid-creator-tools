package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceRegion(int runtimeId, String marker, BlockBounds bounds, CompoundTag data) {
    public static final StreamCodec<FriendlyByteBuf, WorkspaceRegion> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WorkspaceRegion::runtimeId,
            ByteBufCodecs.STRING_UTF8, WorkspaceRegion::marker,
            WorkspaceNetworking.BOUNDS_CODEC, WorkspaceRegion::bounds,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, WorkspaceRegion::data,
            WorkspaceRegion::new
    );

    public WorkspaceRegion withMarker(String marker) {
        return new WorkspaceRegion(this.runtimeId, marker, this.bounds, this.data);
    }

    public WorkspaceRegion withBounds(BlockBounds bounds) {
        return new WorkspaceRegion(this.runtimeId, this.marker, bounds, this.data);
    }

    public WorkspaceRegion withData(CompoundTag data) {
        return new WorkspaceRegion(this.runtimeId, this.marker, this.bounds, data);
    }

    public CompoundTag serialize(CompoundTag tag) {
        tag.putString("marker", this.marker);
        this.bounds.serialize(tag);
        tag.put("data", this.data);
        return tag;
    }

    public static WorkspaceRegion deserialize(int runtimeId, CompoundTag tag) {
        var marker = tag.getStringOr("marker", "");
        var data = tag.getCompoundOrEmpty("data");
        return new WorkspaceRegion(runtimeId, marker, BlockBounds.deserialize(tag), data);
    }
}
