package xyz.nucleoid.creator_tools.workspace.trace;

import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;

public class PartialRegion {
    private final BlockPos origin;
    private BlockPos target;

    public PartialRegion(BlockPos origin) {
        this.origin = origin;
    }

    public void setTarget(BlockPos target) {
        this.target = target;
    }

    public BlockPos getMin() {
        if (this.target == null) {
            return this.origin;
        }
        return BlockPos.min(this.origin, this.target);
    }

    public BlockPos getMax() {
        if (this.target == null) {
            return this.origin;
        }
        return BlockPos.max(this.origin, this.target);
    }

    public BlockBounds asComplete() {
        return BlockBounds.of(this.origin, this.target);
    }
}
