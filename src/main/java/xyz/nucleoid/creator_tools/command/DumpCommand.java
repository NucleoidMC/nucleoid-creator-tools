package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.DataCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.DataCommand.ObjectType;
import net.minecraft.text.Text;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.exporter.DumpExporter;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class DumpCommand {
    // @formatter:off
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var builder = literal("dump").requires(source -> source.hasPermissionLevel(4));

        for (var objectType : DataCommand.TARGET_OBJECT_TYPES) {
            objectType.addArgumentsToBuilder(builder, builderx -> {
                return builderx
                    .then(argument("path", IdentifierArgumentType.identifier())
                        .executes(context -> DumpCommand.dump(context, objectType)));
            });
        }

        dispatcher.register(builder);
    }
    // @formatter:on

    private static int dump(CommandContext<ServerCommandSource> context, ObjectType objectType) throws CommandSyntaxException {
        var source = context.getSource();

        var object = objectType.getObject(context);
        var nbt = object.getNbt();

        var givenIdentifier = IdentifierArgumentType.getIdentifier(context, "path");
        var identifier = CreatorTools.getSourceNameIdentifier(source, givenIdentifier);

        var future = DumpExporter.saveToExport(source.getServer(), nbt, identifier);

        future.handle((v, throwable) -> {
            if (throwable == null) {
                source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.dump.success", identifier), false);
            } else {
                CreatorTools.LOGGER.error("Failed to export object to '{}'", identifier, throwable);
                source.sendError(Text.translatable("text.nucleoid_creator_tools.dump.error"));
            }
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}
