package xyz.nucleoid.creator_tools;

import com.google.common.reflect.Reflection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.creator_tools.command.MapManageCommand;
import xyz.nucleoid.creator_tools.command.MapMetadataCommand;
import xyz.nucleoid.creator_tools.item.CreatorToolsItems;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public final class CreatorTools implements ModInitializer {
    public static final String ID = "nucleoid_creator_tools";

    public static final Logger LOGGER = LogManager.getLogger(CreatorTools.class);

    @Override
    public void onInitialize() {
        Reflection.initialize(CreatorToolsItems.class);

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
            MapManageCommand.register(dispatcher);
            MapMetadataCommand.register(dispatcher);
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            MapWorkspaceManager.get(server).tick();
        });

        ServerPlayNetworking.registerGlobalReceiver(WorkspaceNetworking.OPT_IN_ID, (server, player, handler, buf, responseSender) -> {
            int protocolVersion = buf.readVarInt();
            WorkspaceTraveler.setCreatorToolsProtocolVersion(player, protocolVersion);
        });
    }
}
