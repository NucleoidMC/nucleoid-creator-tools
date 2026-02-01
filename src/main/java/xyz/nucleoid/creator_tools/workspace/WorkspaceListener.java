package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import xyz.nucleoid.map_templates.BlockBounds;

public interface WorkspaceListener {
    default void onSetBounds(BlockBounds bounds) {
    }

    default void onSetOrigin(BlockPos origin) {
    }

    default void onSetData(CompoundTag data) {
    }

    default void onAddRegion(WorkspaceRegion region) {
    }

    default void onRemoveRegion(WorkspaceRegion region) {
    }

    default void onUpdateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
    }
}
