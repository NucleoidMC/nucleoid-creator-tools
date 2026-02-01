package xyz.nucleoid.creator_tools.workspace.editor.payload.c2s;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public record OptInC2SPayload(int protocolVersion) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OptInC2SPayload> ID = WorkspaceNetworking.id("opt_in");

    public static final StreamCodec<ByteBuf, OptInC2SPayload> CODEC = ByteBufCodecs.VAR_INT
            .map(OptInC2SPayload::new, OptInC2SPayload::protocolVersion);

    @Override
    public CustomPacketPayload.Type<OptInC2SPayload> type() {
        return ID;
    }
}
