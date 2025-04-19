package xyz.nucleoid.creator_tools.mixin;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity implements WorkspaceTraveler {
    @Shadow
    @Final
    public MinecraftServer server;

    private ReturnPosition leaveReturn;
    private final Map<RegistryKey<World>, ReturnPosition> workspaceReturns = new Reference2ObjectOpenHashMap<>();

    private int creatorToolsProtocolVersion = WorkspaceNetworking.NO_PROTOCOL_VERSION;

    private ServerPlayerEntityMixin(World world, BlockPos blockPos, float yaw, GameProfile gameProfile) {
        super(world, blockPos, yaw, gameProfile);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
    private void writeData(NbtCompound root, CallbackInfo ci) {
        var creatorTools = new NbtCompound();

        creatorTools.put("workspace_return", ReturnPosition.MAP_CODEC, this.workspaceReturns);
        creatorTools.putNullable("leave_return", ReturnPosition.CODEC, this.leaveReturn);

        root.put(CreatorTools.ID, creatorTools);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
    private void readData(NbtCompound root, CallbackInfo ci) {
        var creatorTools = root.getCompoundOrEmpty(CreatorTools.ID);

        this.workspaceReturns.clear();

        creatorTools.get("workspace_return", ReturnPosition.MAP_CODEC).ifPresent(returns -> {
            this.workspaceReturns.putAll(returns);
        });

        this.leaveReturn = creatorTools.get("leave_return", ReturnPosition.CODEC).orElse(null);
    }

    @Inject(method = "copyFrom", at = @At("RETURN"))
    private void copyFrom(ServerPlayerEntity from, boolean alive, CallbackInfo ci) {
        var fromTraveler = (ServerPlayerEntityMixin) (Object) from;
        this.leaveReturn = fromTraveler.leaveReturn;
        this.workspaceReturns.clear();
        this.workspaceReturns.putAll(fromTraveler.workspaceReturns);
        this.creatorToolsProtocolVersion = fromTraveler.creatorToolsProtocolVersion;
    }

    @Inject(method = "teleportTo", at = @At("HEAD"))
    private void onTeleport(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> ci) {
        this.onDimensionChange(target.world());
    }

    private void onDimensionChange(ServerWorld targetWorld) {
        var sourceDimension = this.getWorld().getRegistryKey();
        var targetDimension = targetWorld.getRegistryKey();

        var workspaceManager = MapWorkspaceManager.get(this.server);
        if (workspaceManager.isWorkspace(sourceDimension)) {
            this.workspaceReturns.put(sourceDimension, ReturnPosition.capture(this));
        } else if (workspaceManager.isWorkspace(targetDimension)) {
            this.leaveReturn = ReturnPosition.capture(this);
        }
    }

    @Nullable
    @Override
    public ReturnPosition getReturnFor(RegistryKey<World> dimension) {
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
