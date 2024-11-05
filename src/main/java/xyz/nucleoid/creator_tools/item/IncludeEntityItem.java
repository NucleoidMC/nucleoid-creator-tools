package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.packettweaker.PacketContext;

public final class IncludeEntityItem extends Item implements PolymerItem {
    public IncludeEntityItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return ActionResult.FAIL;
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        var world = user.getEntityWorld();
        if (!world.isClient()) {
            var workspaceManager = MapWorkspaceManager.get(world.getServer());

            var workspace = workspaceManager.byDimension(world.getRegistryKey());
            if (workspace != null) {
                if (!workspace.getBounds().contains(entity.getBlockPos())) {
                    user.sendMessage(
                            Text.translatable("item.nucleoid_creator_tools.include_entity.target_not_in_map", workspace.getIdentifier())
                                    .formatted(Formatting.RED),
                            false);
                    return ActionResult.FAIL;
                }

                if (workspace.containsEntity(entity.getUuid())) {
                    workspace.removeEntity(entity.getUuid());
                    user.sendMessage(
                            Text.translatable("item.nucleoid_creator_tools.include_entity.removed", workspace.getIdentifier()),
                            true);
                } else {
                    workspace.addEntity(entity.getUuid());
                    user.sendMessage(
                            Text.translatable("item.nucleoid_creator_tools.include_entity.added", workspace.getIdentifier()),
                            true);
                }
                return ActionResult.SUCCESS;
            } else {
                user.sendMessage(Text.translatable("item.nucleoid_creator_tools.include_entity.player_not_in_map").formatted(Formatting.RED),
                        false);
                return ActionResult.FAIL;
            }
        }

        return ActionResult.FAIL;
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.DEBUG_STICK;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return null;
    }
}
