package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class ChunkGeneratorArgument {
    public static final DynamicCommandExceptionType GENERATOR_NOT_FOUND = new DynamicCommandExceptionType(arg ->
            Component.translatableEscape("text.nucleoid_creator_tools.chunk_generator.generator_not_found", arg)
    );

    public static RequiredArgumentBuilder<CommandSourceStack, Identifier> argument(String name) {
        return Commands.argument(name, IdentifierArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                        BuiltInRegistries.CHUNK_GENERATOR.keySet().stream(),
                        builder
                ));
    }

    public static MapCodec<? extends ChunkGenerator> get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        var identifier = IdentifierArgument.getId(context, name);

        var generator = BuiltInRegistries.CHUNK_GENERATOR.getValue(identifier);
        if (generator == null) {
            throw GENERATOR_NOT_FOUND.create(identifier);
        }

        return generator;
    }
}
