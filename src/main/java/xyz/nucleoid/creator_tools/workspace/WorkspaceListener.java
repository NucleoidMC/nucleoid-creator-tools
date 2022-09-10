package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;

public interface WorkspaceListener {
    default void onSetBounds(BlockBounds bounds) {
    }

    default void onSetOrigin(BlockPos origin) {
    }

    default void onSetData(NbtCompound data) {
    }

    default void onAddRegion(WorkspaceRegion region) {
    }

    default void onRemoveRegion(WorkspaceRegion region) {
    }

    default void onUpdateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
    }
}
