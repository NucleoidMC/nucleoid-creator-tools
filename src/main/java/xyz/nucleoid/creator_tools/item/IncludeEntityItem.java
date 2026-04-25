package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;

import java.util.Objects;

@NullMarked
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
        if (!world.isClientSide() && user instanceof ServerPlayer serverUser) {
            var workspaceManager = MapWorkspaceManager.get(Objects.requireNonNull(world.getServer()));

            var workspace = workspaceManager.byDimension(world.dimension());
            if (workspace != null) {
                if (!workspace.getBounds().contains(entity.blockPosition())) {
                    serverUser.sendSystemMessage(
                            Component.translatable("item.nucleoid_creator_tools.include_entity.target_not_in_map", Component.translationArg(workspace.getIdentifier()))
                                    .withStyle(ChatFormatting.RED),
                            false);
                    return InteractionResult.FAIL;
                }

                if (workspace.containsEntity(entity.getUUID())) {
                    workspace.removeEntity(entity.getUUID());
                    serverUser.sendSystemMessage(
                            Component.translatable("item.nucleoid_creator_tools.include_entity.removed", Component.translationArg(workspace.getIdentifier())),
                            true);
                } else {
                    workspace.addEntity(entity.getUUID());
                    serverUser.sendSystemMessage(
                            Component.translatable("item.nucleoid_creator_tools.include_entity.added", Component.translationArg(workspace.getIdentifier())),
                            true);
                }
                return InteractionResult.SUCCESS;
            } else {
                serverUser.sendSystemMessage(Component.translatable("item.nucleoid_creator_tools.include_entity.player_not_in_map").withStyle(ChatFormatting.RED),
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
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return null;
    }
}
