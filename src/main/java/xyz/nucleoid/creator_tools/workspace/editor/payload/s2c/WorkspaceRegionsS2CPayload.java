package xyz.nucleoid.creator_tools.workspace.editor.payload.s2c;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Collection;

public record WorkspaceRegionsS2CPayload(String marker, Collection<Entry> regions) implements CustomPayload {
    public static final CustomPayload.Id<WorkspaceRegionsS2CPayload> ID = WorkspaceNetworking.id("workspace/regions");

    public static final PacketCodec<PacketByteBuf, WorkspaceRegionsS2CPayload> CODEC = PacketCodec.of(WorkspaceRegionsS2CPayload::write, WorkspaceRegionsS2CPayload::read);

    @Override
    public CustomPayload.Id<WorkspaceRegionsS2CPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(this.marker);

        buf.writeCollection(this.regions, (bufx, entry) -> {
            bufx.writeVarInt(entry.runtimeId());
            WorkspaceNetworking.BOUNDS_CODEC.encode(bufx, entry.bounds());
            bufx.writeNbt(entry.data());
        });
    }

    public static WorkspaceRegionsS2CPayload read(PacketByteBuf buf) {
        var marker = buf.readString();

        var entries = buf.readList(bufx -> {
            int runtimeId = bufx.readVarInt();
            var bounds = WorkspaceNetworking.BOUNDS_CODEC.decode(bufx);
            var data = bufx.readNbt();

            return new Entry(runtimeId, bounds, data);
        });

        return new WorkspaceRegionsS2CPayload(marker, entries);
    }

    public record Entry(int runtimeId, BlockBounds bounds, NbtCompound data) {
        public WorkspaceRegion toRegion(String marker) {
            return new WorkspaceRegion(this.runtimeId, marker, this.bounds, this.data);
        }

        public static Entry fromRegion(WorkspaceRegion region) {
            return new Entry(region.runtimeId(), region.bounds(), region.data());
        }
    }
}
