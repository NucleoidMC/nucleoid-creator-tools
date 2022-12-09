package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;
import org.jetbrains.annotations.Nullable;

public interface WorkspaceTraveler {
    @Nullable
    static ReturnPosition getReturnFor(ServerPlayerEntity player, RegistryKey<World> dimension) {
        if (player instanceof WorkspaceTraveler traveler) {
            return traveler.getReturnFor(dimension);
        }
        return null;
    }

    @Nullable
    static ReturnPosition getLeaveReturn(ServerPlayerEntity player) {
        if (player instanceof WorkspaceTraveler traveler) {
            return traveler.getLeaveReturn();
        }
        return null;
    }

    static int getCreatorToolsProtocolVersion(ServerPlayerEntity player) {
        if (player instanceof WorkspaceTraveler traveler) {
            return traveler.getCreatorToolsProtocolVersion();
        }
        return WorkspaceNetworking.NO_PROTOCOL_VERSION;
    }

    static void setCreatorToolsProtocolVersion(ServerPlayerEntity player, int protocolVersion) {
        if (player instanceof WorkspaceTraveler traveler) {
            traveler.setCreatorToolsProtocolVersion(protocolVersion);
        }
    }

    @Nullable
    ReturnPosition getReturnFor(RegistryKey<World> dimension);

    @Nullable
    ReturnPosition getLeaveReturn();

    int getCreatorToolsProtocolVersion();

    void setCreatorToolsProtocolVersion(int protocolVersion);
}
