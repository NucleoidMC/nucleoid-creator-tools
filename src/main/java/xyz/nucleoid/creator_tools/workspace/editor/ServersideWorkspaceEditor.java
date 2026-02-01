package xyz.nucleoid.creator_tools.workspace.editor;

import com.google.common.base.Predicates;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.trace.PartialRegion;
import xyz.nucleoid.creator_tools.workspace.trace.RegionTraceMode;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.function.Predicate;

public final class ServersideWorkspaceEditor implements WorkspaceEditor {
    private static final int PARTICLE_INTERVAL = 10;

    public static final Predicate<String> NO_FILTER = Predicates.alwaysTrue();

    private final ServerPlayer player;
    private final MapWorkspace workspace;

    private RegionTraceMode traceMode = RegionTraceMode.EXACT;
    private PartialRegion tracing;
    private BlockBounds traced;

    private Predicate<String> filter = NO_FILTER;

    private final Int2ObjectMap<Marker> regionToMarker = new Int2ObjectOpenHashMap<>();

    public ServersideWorkspaceEditor(ServerPlayer player, MapWorkspace workspace) {
        this.player = player;
        this.workspace = workspace;
    }

    @Override
    public void tick() {
        if (this.player.tickCount % PARTICLE_INTERVAL == 0) {
            this.renderWorkspaceBounds();
            this.renderTracingBounds();
        }

        if (this.tracing != null && this.player.tickCount % 5 == 0) {
            var pos = this.traceMode.tryTrace(this.player);
            if (pos != null) {
                this.tracing.setTarget(pos);
            }
        }
    }

    @Override
    public boolean useRegionItem() {
        if (!this.player.isShiftKeyDown()) {
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

        Component message = Component.translatable("item.nucleoid_creator_tools.region_visibility_filter." + (this.filter == NO_FILTER ? "no_filter" : "set_filter"));
        this.player.displayClientMessage(message, true);

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
                this.player.displayClientMessage(Component.translatable("item.nucleoid_creator_tools.add_region.trace_mode.commit"), true);
            } else {
                this.tracing = new PartialRegion(pos);
            }
        }
    }

    private void changeTraceMode() {
        var nextMode = this.traceMode.next();
        this.traceMode = nextMode;
        this.player.displayClientMessage(Component.translatable("item.nucleoid_creator_tools.add_region.trace_mode.changed", nextMode.getName()), true);
    }

    @Override
    public void addRegion(WorkspaceRegion region) {
        var marker = this.newMarker(region);
        this.regionToMarker.put(region.runtimeId(), marker);
    }

    @Override
    public void removeRegion(WorkspaceRegion region) {
        var marker = this.regionToMarker.remove(region.runtimeId());
        if (marker != null) {
            marker.destroy();
        }
    }

    @Override
    public void updateRegion(WorkspaceRegion lastRegion, WorkspaceRegion newRegion) {
        var marker = this.regionToMarker.get(newRegion.runtimeId());
        if (marker == null) {
            return;
        }

        marker.update(newRegion, true, this.distanceSquaredToRegion(newRegion));
    }

    private Marker newMarker(WorkspaceRegion region) {
        TextDisplayElement element = new TextDisplayElement();
        element.setSeeThrough(true);
        element.setTextOpacity((byte) 150);
        element.setTextAlignment(Display.TextDisplay.Align.LEFT);
        element.setBillboardMode(Display.BillboardConstraints.CENTER);
        element.setLineWidth(350);

        ElementHolder holder = new ElementHolder();
        holder.addElement(element);
        var attachment = SinglePlayerChunkAttachment.of(holder, this.player.level(), region.bounds().center(), this.player);

        var marker = new Marker(element, attachment);
        marker.update(region, true, this.distanceSquaredToRegion(region));
        return marker;
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
            double distance = this.distanceSquaredToRegion(region);
            var marker = this.regionToMarker.get(region.runtimeId());
            marker.update(region, false, distance);

            if (distance < 32 * 32) {
                int color = colorForRegionBorder(region.marker());
                float red = (color >> 16 & 0xFF) / 255.0F;
                float green = (color >> 8 & 0xFF) / 255.0F;
                float blue = (color & 0xFF) / 255.0F;

                ParticleOutlineRenderer.render(this.player, min, max, red, green, blue);
            }
        }
    }

    private double distanceSquaredToRegion(WorkspaceRegion region) {
        var regionBounds = region.bounds();
        var min = regionBounds.min();
        var max = regionBounds.max();
        return this.player.distanceToSqr(
                (min.getX() + max.getX()) / 2.0,
                (min.getY() + max.getY()) / 2.0,
                (min.getZ() + max.getZ()) / 2.0
        );
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

    public static int colorForRegionBorder(String marker) {
        return HashCommon.mix(marker.hashCode()) & 0xFFFFFF;
    }

    public static int colorForRegionMarkerBackground(String marker) {
        int opacity = 32;
        return (HashCommon.mix(marker.hashCode()) & 0xFFFFFF) | (opacity << 24);
    }

    public static Component textForRegion(WorkspaceRegion region, boolean showDetails) {
        MutableComponent text = Component.empty()
                .append(Component.literal(region.marker()).withStyle(ChatFormatting.BOLD));

        if (!region.data().isEmpty() && showDetails) {
            text
                    .append(CommonComponents.NEW_LINE)
                    .append(new TextComponentTagVisitor("  ").visit(region.data()));
        }

        return text;
    }

    static final class Marker {
        private final TextDisplayElement text;
        private final HolderAttachment billboardAttachment;
        private boolean showingDetails;

        Marker(TextDisplayElement text, HolderAttachment billboardAttachment) {
            this.text = text;
            this.billboardAttachment = billboardAttachment;
            this.showingDetails = false;
        }

        void update(WorkspaceRegion region, boolean regionDirty, double distanceSquared) {
            // Only display NBT if within 32 blocks (distance from which to render particles, too)
            var shouldShowDetails = distanceSquared < 32 * 32;
            var dirty = (shouldShowDetails != showingDetails) || regionDirty;
            showingDetails = shouldShowDetails;

            if (dirty) {
                this.text.setText(textForRegion(region, showingDetails));
                this.text.setBackground(colorForRegionMarkerBackground(region.marker()));
                this.text.tick();
            }
        }

        void destroy() {
            this.billboardAttachment.destroy();
        }
    }
}
