package xyz.nucleoid.creator_tools.mixin;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.ReturnPosition;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

import java.util.Map;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements WorkspaceTraveler {
    @Shadow
    @Final
    private MinecraftServer server;

    @Unique private ReturnPosition leaveReturn;
    @Unique private final Map<ResourceKey<Level>, ReturnPosition> workspaceReturns = new Reference2ObjectOpenHashMap<>();

    @Unique private int creatorToolsProtocolVersion = WorkspaceNetworking.NO_PROTOCOL_VERSION;

    private ServerPlayerMixin(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void writeData(ValueOutput view, CallbackInfo ci) {
        var creatorTools = view.child(CreatorTools.ID);

        creatorTools.store("workspace_return", ReturnPosition.MAP_CODEC, this.workspaceReturns);
        creatorTools.storeNullable("leave_return", ReturnPosition.CODEC, this.leaveReturn);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void readData(ValueInput view, CallbackInfo ci) {
        var creatorTools = view.childOrEmpty(CreatorTools.ID);

        this.workspaceReturns.clear();

        creatorTools.read("workspace_return", ReturnPosition.MAP_CODEC).ifPresent(this.workspaceReturns::putAll);

        this.leaveReturn = creatorTools.read("leave_return", ReturnPosition.CODEC).orElse(null);
    }

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void copyFrom(ServerPlayer from, boolean alive, CallbackInfo ci) {
        var fromTraveler = (ServerPlayerMixin) (Object) from;
        this.leaveReturn = fromTraveler.leaveReturn;
        this.workspaceReturns.clear();
        this.workspaceReturns.putAll(fromTraveler.workspaceReturns);
        this.creatorToolsProtocolVersion = fromTraveler.creatorToolsProtocolVersion;
    }

    @Inject(method = "teleport", at = @At("HEAD"))
    private void onTeleport(TeleportTransition target, CallbackInfoReturnable<ServerPlayer> ci) {
        this.onDimensionChange(target.newLevel());
    }

    @Unique
    private void onDimensionChange(ServerLevel targetLevel) {
        var sourceDimension = this.level().dimension();
        var targetDimension = targetLevel.dimension();

        var workspaceManager = MapWorkspaceManager.get(this.server);
        if (workspaceManager.isWorkspace(sourceDimension)) {
            this.workspaceReturns.put(sourceDimension, ReturnPosition.capture(this));
        } else if (workspaceManager.isWorkspace(targetDimension)) {
            this.leaveReturn = ReturnPosition.capture(this);
        }
    }

    @Nullable
    @Override
    public ReturnPosition getReturnFor(ResourceKey<Level> dimension) {
        return this.workspaceReturns.get(dimension);
    }

    @Nullable
    @Override
    public ReturnPosition getLeaveReturn() {
        return this.leaveReturn;
    }

    @Override
    public int getCreatorToolsProtocolVersion() {
        return this.creatorToolsProtocolVersion;
    }

    @Override
    public void setCreatorToolsProtocolVersion(int protocolVersion) {
        this.creatorToolsProtocolVersion = protocolVersion;
    }
}
