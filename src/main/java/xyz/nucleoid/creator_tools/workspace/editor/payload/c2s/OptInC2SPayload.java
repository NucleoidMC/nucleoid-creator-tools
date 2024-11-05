package xyz.nucleoid.creator_tools.workspace.editor.payload.c2s;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record OptInC2SPayload(int protocolVersion) implements CustomPayload {
    public static final CustomPayload.Id<OptInC2SPayload> ID = WorkspaceNetworking.id("opt_in");

    public static final PacketCodec<ByteBuf, OptInC2SPayload> CODEC = PacketCodecs.VAR_INT
            .xmap(OptInC2SPayload::new, OptInC2SPayload::protocolVersion);

    @Override
    public CustomPayload.Id<OptInC2SPayload> getId() {
        return ID;
    }
}
