package xyz.nucleoid.creator_tools.workspace.editor.payload.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Collection;

public record WorkspaceRegionsS2CPayload(String marker, Collection<Entry> regions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorkspaceRegionsS2CPayload> ID = WorkspaceNetworking.id("workspace/regions");

    public static final StreamCodec<FriendlyByteBuf, WorkspaceRegionsS2CPayload> CODEC = StreamCodec.ofMember(WorkspaceRegionsS2CPayload::write, WorkspaceRegionsS2CPayload::read);

    @Override
    public CustomPacketPayload.Type<WorkspaceRegionsS2CPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.marker);

        buf.writeCollection(this.regions, (bufx, entry) -> {
            bufx.writeVarInt(entry.runtimeId());
            WorkspaceNetworking.BOUNDS_CODEC.encode(bufx, entry.bounds());
            bufx.writeNbt(entry.data());
        });
    }

    public static WorkspaceRegionsS2CPayload read(FriendlyByteBuf buf) {
        var marker = buf.readUtf();

        var entries = buf.readList(bufx -> {
            int runtimeId = bufx.readVarInt();
            var bounds = WorkspaceNetworking.BOUNDS_CODEC.decode(bufx);
            var data = bufx.readNbt();

            return new Entry(runtimeId, bounds, data);
        });

        return new WorkspaceRegionsS2CPayload(marker, entries);
    }

    public record Entry(int runtimeId, BlockBounds bounds, CompoundTag data) {
        public WorkspaceRegion toRegion(String marker) {
            return new WorkspaceRegion(this.runtimeId, marker, this.bounds, this.data);
        }

        public static Entry fromRegion(WorkspaceRegion region) {
            return new Entry(region.runtimeId(), region.bounds(), region.data());
        }
    }
}
