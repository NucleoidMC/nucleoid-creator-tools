package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;

public final class MapWorkspaceArgument {
    public static final DynamicCommandExceptionType WORKSPACE_NOT_FOUND = new DynamicCommandExceptionType(arg ->
            Component.translatableEscape("text.nucleoid_creator_tools.map_workspace.workspace_not_found", arg)
    );

    public static RequiredArgumentBuilder<CommandSourceStack, Identifier> argument(String name) {
        return Commands.argument(name, IdentifierArgument.id())
                .suggests((context, builder) -> {
                    var source = context.getSource();
                    var workspaceManager = MapWorkspaceManager.get(source.getServer());

                    return SharedSuggestionProvider.suggestResource(
                            workspaceManager.getWorkspaceIds().stream(),
                            builder
                    );
                });
    }

    public static MapWorkspace get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        var identifier = IdentifierArgument.getId(context, name);

        var source = context.getSource();
        var workspaceManager = MapWorkspaceManager.get(source.getServer());

        var workspace = workspaceManager.byId(identifier);
        if (workspace == null) {
            throw WORKSPACE_NOT_FOUND.create(identifier);
        }

        return workspace;
    }
}
