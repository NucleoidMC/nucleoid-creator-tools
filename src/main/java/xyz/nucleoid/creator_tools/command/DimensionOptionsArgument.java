package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.LevelStem;

public final class DimensionOptionsArgument {
    public static final DynamicCommandExceptionType DIMENSION_NOT_FOUND = new DynamicCommandExceptionType(arg ->
            Component.translatableEscape("text.nucleoid_creator_tools.dimension_options.dimension_not_found", arg)
    );

    public static RequiredArgumentBuilder<CommandSourceStack, Identifier> argument(String name) {
        return Commands.argument(name, IdentifierArgument.id())
                .suggests((context, builder) -> {
                    var source = context.getSource();
                    var registryManager = source.getServer().registries().compositeAccess();
                    var dimensions = registryManager.lookupOrThrow(Registries.LEVEL_STEM);

                    return SharedSuggestionProvider.suggestResource(
                            dimensions.keySet().stream(),
                            builder
                    );
                });
    }

    public static LevelStem get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        var identifier = IdentifierArgument.getId(context, name);

        var source = context.getSource();
        var registryManager = source.getServer().registries().compositeAccess();
        var dimensions = registryManager.lookupOrThrow(Registries.LEVEL_STEM);

        var dimension = dimensions.getValue(identifier);
        if (dimension == null) {
            throw DIMENSION_NOT_FOUND.create(identifier);
        }

        return dimension;
    }
}
