package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import xyz.nucleoid.creator_tools.component.CreatorToolsDataComponentTypes;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.editor.ServersideWorkspaceEditor;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RegionVisibilityFilterItem extends Item implements PolymerItem {
    public RegionVisibilityFilterItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (world.isClientSide()) {
            return super.use(world, player, hand);
        }

        var stack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            var workspaceManager = MapWorkspaceManager.get(world.getServer());
            var editor = workspaceManager.getEditorFor(serverPlayer);

            var regions = getRegions(stack);
            Predicate<String> filter = regions == null || player.isShiftKeyDown() ? ServersideWorkspaceEditor.NO_FILTER : regions::contains;
            
            if (editor != null && editor.applyFilter(filter)) {
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
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
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltipType, PacketContext context) {
        var displayStack = PolymerItem.super.getPolymerItemStack(stack, tooltipType, context);
        var regions = getRegions(stack);

        if (regions != null && !regions.isEmpty()) {
            var region = regions.getFirst();

            displayStack.set(DataComponents.DYED_COLOR, new DyedItemColor(ServersideWorkspaceEditor.colorForRegionBorder(region)));
            displayStack.update(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT, display -> display.withHidden(DataComponents.DYED_COLOR, true));
        }

        return displayStack;
    }

    @SuppressWarnings("deprecation") // not deprecated
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent, Consumer<Component> textConsumer, TooltipFlag type) {
        var regions = getRegions(stack);

        if (regions != null) {
            for (var region : regions) {
                textConsumer.accept(Component.literal(region).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Nullable
    private static List<String> getRegions(ItemStack stack) {
        var component = stack.get(CreatorToolsDataComponentTypes.REGION_VISIBILITY_FILTER);
        return component == null ? null : component.regions();
    }
}
