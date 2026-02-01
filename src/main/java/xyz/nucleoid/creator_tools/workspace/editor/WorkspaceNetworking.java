package xyz.nucleoid.creator_tools.workspace.editor;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.creator_tools.workspace.editor.payload.*;
import xyz.nucleoid.creator_tools.workspace.editor.payload.c2s.OptInC2SPayload;
import xyz.nucleoid.creator_tools.workspace.editor.payload.c2s.WorkspaceNewC2SPayload;
import xyz.nucleoid.creator_tools.workspace.editor.payload.c2s.WorkspaceRegionAddC2SPayload;
import xyz.nucleoid.creator_tools.workspace.editor.payload.s2c.WorkspaceEnterS2CPayload;
import xyz.nucleoid.creator_tools.workspace.editor.payload.s2c.WorkspaceRegionsS2CPayload;
import xyz.nucleoid.map_templates.BlockBounds;

public final class WorkspaceNetworking {
    public static final int NO_PROTOCOL_VERSION = -1;

    public static final StreamCodec<FriendlyByteBuf, BlockBounds> BOUNDS_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlockBounds::min,
            BlockPos.STREAM_CODEC, BlockBounds::max,
            BlockBounds::of
    );

    private WorkspaceNetworking() {
    }

    public static void register() {
        // Client <-- Server
        registerBidirectionalPayloads(PayloadTypeRegistry.playS2C());
        PayloadTypeRegistry.playS2C().register(WorkspaceEnterS2CPayload.ID, WorkspaceEnterS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WorkspaceRegionsS2CPayload.ID, WorkspaceRegionsS2CPayload.CODEC);

        // Client --> Server
        registerBidirectionalPayloads(PayloadTypeRegistry.playC2S());
        PayloadTypeRegistry.playC2S().register(OptInC2SPayload.ID, OptInC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WorkspaceNewC2SPayload.ID, WorkspaceNewC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WorkspaceRegionAddC2SPayload.ID, WorkspaceRegionAddC2SPayload.CODEC);

        // Receivers
        ServerPlayNetworking.registerGlobalReceiver(OptInC2SPayload.ID, (payload, context) ->
                WorkspaceTraveler.setCreatorToolsProtocolVersion(context.player(), payload.protocolVersion()));
    }

    private static void registerBidirectionalPayloads(PayloadTypeRegistry<?> registry) {
        registry.register(WorkspaceLeavePayload.ID, WorkspaceLeavePayload.CODEC);
        registry.register(WorkspaceBoundsPayload.ID, WorkspaceBoundsPayload.CODEC);
        registry.register(WorkspaceDataPayload.ID, WorkspaceDataPayload.CODEC);
        registry.register(WorkspaceRegionPayload.ID, WorkspaceRegionPayload.CODEC);
        registry.register(WorkspaceRegionRemovePayload.ID, WorkspaceRegionRemovePayload.CODEC);
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String path) {
        return new CustomPacketPayload.Type<>(CreatorTools.identifier(path));
    }
}
