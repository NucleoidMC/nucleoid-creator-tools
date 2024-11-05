package xyz.nucleoid.creator_tools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.command.MapManageCommand;
import xyz.nucleoid.creator_tools.command.MapMetadataCommand;
import xyz.nucleoid.creator_tools.component.CreatorToolsDataComponentTypes;
import xyz.nucleoid.creator_tools.item.CreatorToolsItems;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.editor.WorkspaceNetworking;

public final class CreatorTools implements ModInitializer {
    public static final String ID = "nucleoid_creator_tools";

    public static final Logger LOGGER = LogManager.getLogger(CreatorTools.class);

    @Override
    public void onInitialize() {
        CreatorToolsItems.register();
        CreatorToolsDataComponentTypes.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
            MapManageCommand.register(dispatcher);
            MapMetadataCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(MapWorkspaceManager::migratePath);

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            MapWorkspaceManager.get(server).tick();
        });

        WorkspaceNetworking.register();
    }

    public static Identifier identifier(String path) {
        return Identifier.of(ID, path);
    }
}
