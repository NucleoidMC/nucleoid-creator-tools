package xyz.nucleoid.creator_tools.workspace.editor;

import net.minecraft.util.math.BlockPos;
import java.util.function.Predicate;
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

    default boolean useRegionItem() {
        return false;
    }

    default boolean applyFilter(Predicate<String> regions) {
        return false;
    }

    @Nullable
    default BlockBounds takeTracedRegion() {
        return null;
    }

    default void tick() {
    }
}
