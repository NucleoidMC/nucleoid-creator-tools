package xyz.nucleoid.creator_tools.workspace.editor.payload.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NullMarked;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NullMarked
public record WorkspaceRegionsS2CPayload(String marker, List<Entry> regions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceRegionsS2CPayload> ID = WorkspaceNetworking.id("workspace/regions");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceRegionsS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WorkspaceRegionsS2CPayload::marker,
            Entry.CODEC.apply(ByteBufCodecs.list()), WorkspaceRegionsS2CPayload::regions,
            WorkspaceRegionsS2CPayload::new
    );

    @Override
    public CustomPacketPayload.Type<WorkspaceRegionsS2CPayload> type() {
        return ID;
    }

    public record Entry(int runtimeId, BlockBounds bounds, CompoundTag data) {
        public static final StreamCodec<FriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::runtimeId,
                WorkspaceNetworking.BOUNDS_CODEC, Entry::bounds,
                ByteBufCodecs.COMPOUND_TAG, Entry::data,
                Entry::new
        );


        public WorkspaceRegion toRegion(String marker) {
            return new WorkspaceRegion(this.runtimeId, marker, this.bounds, this.data);
        }

        public static Entry fromRegion(WorkspaceRegion region) {
            return new Entry(region.runtimeId(), region.bounds(), region.data());
        }
    }
}
