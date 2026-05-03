package xyz.nucleoid.creator_tools.workspace.editor;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.creator_tools.CreatorTools;

import java.util.Collection;
import java.util.UUID;

public class SinglePlayerChunkAttachment implements HolderAttachment {
    private final ElementHolder holder;
    private final LevelChunk chunk;
    protected Vec3 pos;
    private final UUID player;

    public SinglePlayerChunkAttachment(ElementHolder holder, LevelChunk chunk, Vec3 position, ServerPlayer player) {
        this.chunk = chunk;
        this.pos = position;
        this.holder = holder;
        this.player = player.getUUID();
        this.attach();
    }

    protected void attach() {
        ((HolderAttachmentHolder) chunk).polymerVE$addHolder(this);
        this.holder.setAttachment(this);
    }

    public static HolderAttachment of(ElementHolder holder, ServerLevel level, Vec3 pos, ServerPlayer player) {
        var chunk = level.getChunk(BlockPos.containing(pos));

        if (chunk instanceof LevelChunk chunk1) {
            return new SinglePlayerChunkAttachment(holder, chunk1, pos, player);
        } else {
            CreatorTools.LOGGER.warn("We tried to attach at {}, but it isn't loaded!", BlockPos.containing(pos).toShortString(), new NullPointerException());
            return new ManualAttachment(holder, level, () -> pos);
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
    public void updateCurrentlyTracking(Collection<ServerGamePacketListenerImpl> currentlyTracking) {
        assert currentlyTracking.size() <= 1;

        ServerPlayer watching = null;
        for (ServerPlayer x : ((ServerChunkCache) this.chunk.getLevel().getChunkSource()).chunkMap.getPlayers(this.chunk.getPos(), false)) {
            if (x.getUUID().equals(this.player)) {
                watching = x;
                break;
            }
        }

        if (watching != null) {
            this.holder.startWatching(watching.connection);
        } else {
            for (ServerGamePacketListenerImpl handler : currentlyTracking) {
                this.holder.stopWatching(handler);
            }
        }
    }

    @Override
    public void updateTracking(ServerGamePacketListenerImpl tracking) {
        if (tracking.player.isDeadOrDying() || !VirtualEntityUtils.isPlayerTracking(tracking.getPlayer(), this.chunk)) {
            VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.stopWatching(tracking));
        }
    }

    @Override
    public Vec3 getPos() {
        return this.pos;
    }

    @Override
    public ServerLevel getWorld() {
        return (ServerLevel) this.chunk.getLevel();
    }
}
