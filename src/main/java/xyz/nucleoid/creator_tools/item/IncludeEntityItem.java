package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.packettweaker.PacketContext;

public final class IncludeEntityItem extends Item implements PolymerItem {
    public IncludeEntityItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        var world = user.level();
        if (!world.isClientSide()) {
            var workspaceManager = MapWorkspaceManager.get(world.getServer());

            var workspace = workspaceManager.byDimension(world.dimension());
            if (workspace != null) {
                if (!workspace.getBounds().contains(entity.blockPosition())) {
                    user.displayClientMessage(
                            Component.translatable("item.nucleoid_creator_tools.include_entity.target_not_in_map", workspace.getIdentifier())
                                    .withStyle(ChatFormatting.RED),
                            false);
                    return InteractionResult.FAIL;
                }

                if (workspace.containsEntity(entity.getUUID())) {
                    workspace.removeEntity(entity.getUUID());
                    user.displayClientMessage(
                            Component.translatable("item.nucleoid_creator_tools.include_entity.removed", workspace.getIdentifier()),
                            true);
                } else {
                    workspace.addEntity(entity.getUUID());
                    user.displayClientMessage(
                            Component.translatable("item.nucleoid_creator_tools.include_entity.added", workspace.getIdentifier()),
                            true);
                }
                return InteractionResult.SUCCESS;
            } else {
                user.displayClientMessage(Component.translatable("item.nucleoid_creator_tools.include_entity.player_not_in_map").withStyle(ChatFormatting.RED),
                        false);
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.FAIL;
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
