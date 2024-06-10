package xyz.nucleoid.creator_tools.workspace.editor;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import xyz.nucleoid.creator_tools.CreatorTools;

import java.util.Collection;
import java.util.UUID;

public class SinglePlayerChunkAttachment implements HolderAttachment {
    private final ElementHolder holder;
    private final WorldChunk chunk;
    protected Vec3d pos;
    private final UUID player;

    public SinglePlayerChunkAttachment(ElementHolder holder, WorldChunk chunk, Vec3d position, ServerPlayerEntity player) {
        this.chunk = chunk;
        this.pos = position;
        this.holder = holder;
        this.player = player.getUuid();
        this.attach();
    }

    protected void attach() {
        ((HolderAttachmentHolder) chunk).polymerVE$addHolder(this);
        this.holder.setAttachment(this);
    }

    public static HolderAttachment of(ElementHolder holder, ServerWorld world, Vec3d pos, ServerPlayerEntity player) {
        var chunk = world.getChunk(BlockPos.ofFloored(pos));

        if (chunk instanceof WorldChunk chunk1) {
            return new SinglePlayerChunkAttachment(holder, chunk1, pos, player);
        } else {
            CreatorTools.LOGGER.warn("We tried to attach at {}, but it isn't loaded!", BlockPos.ofFloored(pos).toShortString(), new NullPointerException());
            return new ManualAttachment(holder, world, () -> pos);
        }
    }

    @Override
    public ElementHolder holder() {
        return this.holder;
    }

    @Override
    public void destroy() {
        if (this.holder.getAttachment() == this) {
            this.holder.setAttachment(null);
        }
        ((HolderAttachmentHolder) chunk).polymerVE$removeHolder(this);
    }

    @Override
    public void updateCurrentlyTracking(Collection<ServerPlayNetworkHandler> currentlyTracking) {
        assert currentlyTracking.size() <= 1;

        ServerPlayerEntity watching = null;
        for (ServerPlayerEntity x : ((ServerChunkManager) this.chunk.getWorld().getChunkManager()).threadedAnvilChunkStorage.getPlayersWatchingChunk(this.chunk.getPos(), false)) {
            if (x.getUuid().equals(this.player)) {
                watching = x;
                break;
            }
        }

        if (watching != null) {
            this.holder.startWatching(watching.networkHandler);
        } else {
            for (ServerPlayNetworkHandler handler : currentlyTracking) {
                this.holder.stopWatching(handler);
            }
        }
    }

    @Override
    public void updateTracking(ServerPlayNetworkHandler tracking) {
        if (tracking.player.isDead() || !VirtualEntityUtils.isPlayerTracking(tracking.getPlayer(), this.chunk)) {
            VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.stopWatching(tracking));
        }
    }

    @Override
    public Vec3d getPos() {
        return this.pos;
    }

    @Override
    public ServerWorld getWorld() {
        return (ServerWorld) this.chunk.getWorld();
    }
}
