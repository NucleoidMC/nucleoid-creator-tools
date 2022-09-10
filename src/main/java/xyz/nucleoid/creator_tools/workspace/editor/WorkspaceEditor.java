package xyz.nucleoid.creator_tools.workspace.editor;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.map_templates.BlockBounds;

public interface WorkspaceEditor {
    default void addRegion(WorkspaceRegion region) {
    }

    default void updateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
    }

    default void removeRegion(WorkspaceRegion region) {
    }

    default void setBounds(BlockBounds bounds) {
    }

    default void setOrigin(BlockPos origin) {
    }

    default void setData(NbtCompound data) {
    }

    default boolean useRegionItem() {
        return false;
    }

    @Nullable
    default BlockBounds takeTracedRegion() {
        return null;
    }

    default void onEnter() {
    }

    default void onLeave() {
    }

    default void tick() {
    }
}
