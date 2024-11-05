package xyz.nucleoid.creator_tools.item;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.creator_tools.component.CreatorToolsDataComponentTypes;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.editor.ServersideWorkspaceEditor;
import xyz.nucleoid.packettweaker.PacketContext;

public final class RegionVisibilityFilterItem extends Item implements PolymerItem {
    public RegionVisibilityFilterItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            return super.use(world, player, hand);
        }

        var stack = player.getStackInHand(hand);

        if (player instanceof ServerPlayerEntity serverPlayer) {
            var workspaceManager = MapWorkspaceManager.get(serverPlayer.server);
            var editor = workspaceManager.getEditorFor(serverPlayer);

            var regions = getRegions(stack);
            Predicate<String> filter = regions == null || player.isSneaking() ? ServersideWorkspaceEditor.NO_FILTER : regions::contains;
            
            if (editor != null && editor.applyFilter(filter)) {
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.LEATHER_LEGGINGS;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return null;
    }

    @Override
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipType tooltipType, PacketContext context) {
        var displayStack = PolymerItem.super.getPolymerItemStack(stack, tooltipType, context);
        var regions = getRegions(stack);

        if (regions != null && !regions.isEmpty()) {
            var region = regions.get(0);
            displayStack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(ServersideWorkspaceEditor.colorForRegionBorder(region), false));
        }

        return displayStack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        var regions = getRegions(stack);

        if (regions != null) {
            for (var region : regions) {
                tooltip.add(Text.literal(region).formatted(Formatting.GRAY));
            }
        }
    }

    @Nullable
    private static List<String> getRegions(ItemStack stack) {
        var component = stack.get(CreatorToolsDataComponentTypes.REGION_VISIBILITY_FILTER);
        return component == null ? null : component.regions();
    }
}
