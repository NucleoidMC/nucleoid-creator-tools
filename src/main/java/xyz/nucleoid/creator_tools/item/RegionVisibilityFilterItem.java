package xyz.nucleoid.creator_tools.item;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import eu.pb4.polymer.api.item.PolymerItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.editor.ServersideWorkspaceEditor;

public final class RegionVisibilityFilterItem extends Item implements PolymerItem {
    private static final String REGION_KEY = "Region";

    public RegionVisibilityFilterItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
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
                return TypedActionResult.success(stack);
            }
        }

        return TypedActionResult.pass(stack);
    }

    @Override
    public Item getPolymerItem(ItemStack stack, ServerPlayerEntity player) {
        return Items.LEATHER_LEGGINGS;
    }

    @Override
    public int getPolymerArmorColor(ItemStack stack, ServerPlayerEntity player) {
        var regions = getRegions(stack);
        if (regions == null || regions.isEmpty()) return -1;

        var region = regions.get(0);
        return ServersideWorkspaceEditor.colorForRegionMarker(region);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        var regions = getRegions(stack);

        if (regions != null) {
            for (var region : regions) {
                tooltip.add(Text.literal(region).formatted(Formatting.GRAY));
            }
        }
    }

    @Nullable
    private static List<String> getRegions(ItemStack stack) {
        var nbt = stack.getNbt();
        if (nbt == null) return null;

        return switch (nbt.getType(REGION_KEY)) {
            case NbtElement.LIST_TYPE -> nbt.getList(REGION_KEY, NbtElement.STRING_TYPE).stream()
                .map(NbtElement::asString)
                .toList();
            case NbtElement.STRING_TYPE -> List.of(nbt.getString(REGION_KEY));
            default -> null;
        };
    }
}
