package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.nbt.NbtCompound;
import xyz.nucleoid.map_templates.BlockBounds;

public record WorkspaceRegion(int runtimeId, String marker, BlockBounds bounds, NbtCompound data) {
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
        var marker = tag.getString("marker");
        var data = tag.getCompound("data");
        return new WorkspaceRegion(runtimeId, marker, BlockBounds.deserialize(tag), data);
    }
}
