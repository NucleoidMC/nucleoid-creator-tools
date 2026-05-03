package xyz.nucleoid.creator_tools.workspace.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public enum RegionTraceMode {
    OFFSET(Component.translatable("item.nucleoid_creator_tools.add_region.trace_mode.offset")),
    EXACT(Component.translatable("item.nucleoid_creator_tools.add_region.trace_mode.exact")),
    AT_FEET(Component.translatable("item.nucleoid_creator_tools.add_region.trace_mode.at_feet"));

    private final Component name;

    RegionTraceMode(Component name) {
        this.name = name;
    }

    @Nullable
    public BlockPos tryTrace(Player player) {
        if (this == AT_FEET) {
            return player.blockPosition();
        }

        var traceResult = player.pick(64.0, 1.0F, true);
        if (traceResult.getType() == HitResult.Type.BLOCK) {
            var blockResult = (BlockHitResult) traceResult;
            var pos = blockResult.getBlockPos();

            if (this == OFFSET) {
                pos = pos.relative(blockResult.getDirection());
            }

            return pos;
        }

        return null;
    }

    public RegionTraceMode next() {
        var modes = values();
        return modes[(this.ordinal() + 1) % modes.length];
    }

    public Component getName() {
        return this.name;
    }
}
