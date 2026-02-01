package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public interface WorkspaceTraveler {
    @Nullable
    static ReturnPosition getReturnFor(ServerPlayer player, ResourceKey<Level> dimension) {
        if (player instanceof WorkspaceTraveler traveler) {
            return traveler.getReturnFor(dimension);
        }
        return null;
    }

    @Nullable
    static ReturnPosition getLeaveReturn(ServerPlayer player) {
        if (player instanceof WorkspaceTraveler traveler) {
            return traveler.getLeaveReturn();
        }
        return null;
    }

    static int getCreatorToolsProtocolVersion(ServerPlayer player) {
        if (player instanceof WorkspaceTraveler traveler) {
            return traveler.getCreatorToolsProtocolVersion();
        }
        return WorkspaceNetworking.NO_PROTOCOL_VERSION;
    }

    static void setCreatorToolsProtocolVersion(ServerPlayer player, int protocolVersion) {
        if (player instanceof WorkspaceTraveler traveler) {
            traveler.setCreatorToolsProtocolVersion(protocolVersion);
        }
    }

    @Nullable
    ReturnPosition getReturnFor(ResourceKey<Level> dimension);

    @Nullable
    ReturnPosition getLeaveReturn();

    int getCreatorToolsProtocolVersion();

    void setCreatorToolsProtocolVersion(int protocolVersion);
}
