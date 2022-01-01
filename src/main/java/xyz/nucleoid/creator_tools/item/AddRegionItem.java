package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.api.item.PolymerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;

public final class AddRegionItem extends Item implements PolymerItem {
    public AddRegionItem(Settings settings) {
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

            if (editor != null && editor.useRegionItem()) {
                return TypedActionResult.success(stack);
            }
        }

        return TypedActionResult.pass(stack);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, ServerPlayerEntity player) {
        return Items.STICK;
    }
}
