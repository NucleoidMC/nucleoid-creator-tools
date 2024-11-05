package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.packettweaker.PacketContext;

public final class AddRegionItem extends Item implements PolymerItem {
    public AddRegionItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            return super.use(world, player, hand);
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            var workspaceManager = MapWorkspaceManager.get(serverPlayer.server);
            var editor = workspaceManager.getEditorFor(serverPlayer);

            if (editor != null && editor.useRegionItem()) {
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.STICK;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return null;
    }
}
