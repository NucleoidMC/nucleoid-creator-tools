package xyz.nucleoid.creator_tools.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Final
    @Shadow
    private MinecraftServer server;
    @Inject(method = "addPlayer", at = @At("RETURN"))
    private void addPlayer(ServerPlayer player, CallbackInfo ci) {
        MapWorkspaceManager.get(server).onPlayerAddToWorld(player, (ServerLevel) (Object) this);
    }

    @Inject(method = "removePlayerImmediately", at = @At("RETURN"))
    private void removePlayer(ServerPlayer player, Entity.RemovalReason reason, CallbackInfo ci) {
        MapWorkspaceManager.get(server).onPlayerRemoveFromWorld(player, (ServerLevel) (Object) this);
    }
}
