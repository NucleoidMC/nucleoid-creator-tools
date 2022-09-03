package xyz.nucleoid.creator_tools.workspace.editor;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import com.google.common.base.Predicates;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.trace.PartialRegion;
import xyz.nucleoid.creator_tools.workspace.trace.RegionTraceMode;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.UUID;
import java.util.function.Predicate;

public final class ServersideWorkspaceEditor implements WorkspaceEditor {
    private static final int PARTICLE_INTERVAL = 10;

    public static final Predicate<String> NO_FILTER = Predicates.alwaysTrue();

    private final ServerPlayerEntity player;
    private final MapWorkspace workspace;

    private RegionTraceMode traceMode = RegionTraceMode.EXACT;
    private PartialRegion tracing;
    private BlockBounds traced;

    private Predicate<String> filter = NO_FILTER;

    private final ArmorStandEntity markerEntity;
    private final Int2ObjectMap<Marker> regionToMarker = new Int2ObjectOpenHashMap<>();

    private int nextMarkerId = -1;

    public ServersideWorkspaceEditor(ServerPlayerEntity player, MapWorkspace workspace) {
        this.player = player;
        this.workspace = workspace;

        var markerEntity = new ArmorStandEntity(EntityType.ARMOR_STAND, player.world);
        markerEntity.setInvisible(true);
        markerEntity.setInvulnerable(true);
        markerEntity.setNoGravity(true);
        markerEntity.setMarker(true);
        markerEntity.setCustomNameVisible(true);
        this.markerEntity = markerEntity;
    }

    @Override
    public void tick() {
        if (this.player.age % PARTICLE_INTERVAL == 0) {
            this.renderWorkspaceBounds();
            this.renderTracingBounds();
        }

        if (this.tracing != null && this.player.age % 5 == 0) {
            var pos = this.traceMode.tryTrace(this.player);
            if (pos != null) {
                this.tracing.setTarget(pos);
            }
        }
    }

    @Override
    public boolean useRegionItem() {
        if (!this.player.isSneaking()) {
            this.updateTrace();
        } else {
            this.changeTraceMode();
        }
        return true;
    }

    @Override
    public boolean applyFilter(Predicate<String> filter) {
        Predicate<String> oldFilter = this.filter;
        this.filter = filter;

        if (this.filter == oldFilter) {
            return false;
        }

        for (var region : workspace.getRegions()) {
            boolean previouslyVisible = oldFilter.test(region.marker());
            boolean nowVisible = this.filter.test(region.marker());

            if (previouslyVisible && !nowVisible) {
                this.removeRegion(region);
            } else if (!previouslyVisible && nowVisible) {
                this.addRegion(region);
            }
        }

        Text message = Text.translatable("item.nucleoid_creator_tools.region_visibility_filter." + (this.filter == NO_FILTER ? "no_filter" : "set_filter"));
        this.player.sendMessage(message, true);

        return true;
    }

    private boolean isRegionVisible(WorkspaceRegion region) {
        return this.filter.test(region.marker());
    }

    @Override
    @Nullable
    public BlockBounds takeTracedRegion() {
        var traced = this.traced;
        this.traced = null;
        return traced;
    }

    private void updateTrace() {
        var pos = this.traceMode.tryTrace(this.player);
        if (pos != null) {
            var tracing = this.tracing;
            if (tracing != null) {
                tracing.setTarget(pos);
                this.traced = tracing.asComplete();
                this.tracing = null;
                this.player.sendMessage(Text.translatable("item.nucleoid_creator_tools.add_region.trace_mode.commit"), true);
            } else {
                this.tracing = new PartialRegion(pos);
            }
        }
    }

    private void changeTraceMode() {
        var nextMode = this.traceMode.next();
        this.traceMode = nextMode;
        this.player.sendMessage(Text.translatable("item.nucleoid_creator_tools.add_region.trace_mode.changed", nextMode.getName()), true);
    }

    @Override
    public void addRegion(WorkspaceRegion region) {
        var marker = this.nextMarkerIds();
        var markerPos = region.bounds().center();

        var markerEntity = marker.applyTo(this.markerEntity);
        markerEntity.setPos(markerPos.x, markerPos.y, markerPos.z);
        markerEntity.setCustomName(Text.literal(region.marker()));

        var networkHandler = this.player.networkHandler;
        networkHandler.sendPacket(markerEntity.createSpawnPacket());
        networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(marker.id(), markerEntity.getDataTracker(), true));

        this.regionToMarker.put(region.runtimeId(), marker);
    }

    @Override
    public void removeRegion(WorkspaceRegion region) {
        var marker = this.regionToMarker.remove(region.runtimeId());
        if (marker != null) {
            this.player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(marker.id()));
        }
    }

    @Override
    public void updateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
        var marker = this.regionToMarker.get(newRegion.runtimeId());
        if (marker == null) {
            return;
        }

        var markerEntity = marker.applyTo(this.markerEntity);
        markerEntity.setCustomName(Text.literal(newRegion.marker()));

        this.player.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(marker.id(), markerEntity.getDataTracker(), true));
    }

    private Marker nextMarkerIds() {
        int id = this.nextMarkerId--;
        var uuid = UUID.randomUUID();
        return new Marker(id, uuid);
    }

    private void renderWorkspaceBounds() {
        var workspace = this.workspace;
        var bounds = workspace.getBounds();
        ParticleOutlineRenderer.render(this.player, bounds.min(), bounds.max(), 1.0F, 0.0F, 0.0F);

        for (var region : workspace.getRegions()) {
            if (!this.isRegionVisible(region)) continue;

            var regionBounds = region.bounds();
            var min = regionBounds.min();
            var max = regionBounds.max();
            double distance = this.player.squaredDistanceTo(
                    (min.getX() + max.getX()) / 2.0,
                    (min.getY() + max.getY()) / 2.0,
                    (min.getZ() + max.getZ()) / 2.0
            );

            if (distance < 32 * 32) {
                int color = colorForRegionMarker(region.marker());
                float red = (color >> 16 & 0xFF) / 255.0F;
                float green = (color >> 8 & 0xFF) / 255.0F;
                float blue = (color & 0xFF) / 255.0F;

                ParticleOutlineRenderer.render(this.player, min, max, red, green, blue);
            }
        }
    }

    private void renderTracingBounds() {
        var tracing = this.tracing;
        var traced = this.traced;
        if (tracing != null) {
            ParticleOutlineRenderer.render(this.player, tracing.getMin(), tracing.getMax(), 0.0F, 0.8F, 0.0F);
        } else if (traced != null) {
            ParticleOutlineRenderer.render(this.player, traced.min(), traced.max(), 0.1F, 1.0F, 0.1F);
        }
    }

    public static int colorForRegionMarker(String marker) {
        return HashCommon.mix(marker.hashCode()) & 0xFFFFFF;
    }

    static final record Marker(int id, UUID uuid) {
        <T extends Entity> T applyTo(T entity) {
            entity.setId(this.id);
            entity.setUuid(this.uuid);
            return entity;
        }
    }
}
