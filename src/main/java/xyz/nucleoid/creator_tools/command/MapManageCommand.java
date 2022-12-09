package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.MapTemplateExporter;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MapManageCommand {
    public static final SimpleCommandExceptionType MAP_NOT_HERE = new SimpleCommandExceptionType(
            Text.translatable("text.nucleoid_creator_tools.map.map_not_here")
    );

    public static final DynamicCommandExceptionType MAP_ALREADY_EXISTS = new DynamicCommandExceptionType(arg ->
            Text.translatable("text.nucleoid_creator_tools.map.open.map_already_exists", arg)
    );

    public static final SimpleCommandExceptionType MAP_MISMATCH = new SimpleCommandExceptionType(
            Text.translatable("text.nucleoid_creator_tools.map.delete.map_mismatch")
    );

    public static final DynamicCommandExceptionType INVALID_GENERATOR_CONFIG = new DynamicCommandExceptionType(arg ->
            Text.translatable("text.nucleoid_creator_tools.map.open.invalid_generator_config", arg)
    );

    // @formatter:off
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("map").requires(source -> source.hasPermissionLevel(4))
                .then(literal("open")
                    .then(argument("workspace", IdentifierArgumentType.identifier())
                    .executes(context -> MapManageCommand.openWorkspace(context, null))
                        .then(literal("like")
                            .then(DimensionOptionsArgument.argument("dimension")
                            .executes(MapManageCommand::openWorkspaceLikeDimension)
                        ))
                        .then(literal("with")
                            .then(ChunkGeneratorArgument.argument("generator")
                            .then(argument("config", NbtCompoundArgumentType.nbtCompound())
                            .executes(MapManageCommand::openWorkspaceByGenerator)
                        )))
                ))
                .then(literal("origin")
                    .then(MapWorkspaceArgument.argument("workspace")
                    .then(argument("origin", BlockPosArgumentType.blockPos())
                    .executes(MapManageCommand::setWorkspaceOrigin)
                )))
                .then(literal("bounds")
                    .then(MapWorkspaceArgument.argument("workspace")
                        .executes(MapManageCommand::getWorkspaceBounds)
                        .then(argument("min", BlockPosArgumentType.blockPos())
                            .then(argument("max", BlockPosArgumentType.blockPos())
                            .executes(MapManageCommand::setWorkspaceBounds)
                        ))
                ))
                .then(literal("join")
                    .then(MapWorkspaceArgument.argument("workspace")
                    .executes(MapManageCommand::joinWorkspace)
                ))
                .then(literal("leave").executes(MapManageCommand::leaveMap))
                .then(literal("export")
                    .then(MapWorkspaceArgument.argument("workspace")
                    .executes(context -> MapManageCommand.exportMap(context, false))
                    .then(literal("withEntities")
                        .executes(context -> MapManageCommand.exportMap(context, true))
                    )
                ))
                .then(literal("delete")
                    .then(MapWorkspaceArgument.argument("workspace_once")
                    .then(MapWorkspaceArgument.argument("workspace_again")
                    .executes(MapManageCommand::deleteWorkspace)
                )))
                .then(literal("import")
                    .then(argument("location", IdentifierArgumentType.identifier())
                    .then(argument("to_workspace", IdentifierArgumentType.identifier())
                        .then(argument("origin", BlockPosArgumentType.blockPos())
                            .executes(context -> {
                                BlockPos origin = BlockPosArgumentType.getBlockPos(context, "origin");
                                return MapManageCommand.importWorkspace(context, origin);
                            })
                        )
                    .executes(context -> MapManageCommand.importWorkspace(context, BlockPos.ORIGIN))
                )))
        );
    }
    // @formatter:on

    private static int openWorkspace(CommandContext<ServerCommandSource> context, RuntimeWorldConfig worldConfig) throws CommandSyntaxException {
        var source = context.getSource();

        var givenIdentifier = IdentifierArgumentType.getIdentifier(context, "workspace");

        Identifier identifier;
        if (givenIdentifier.getNamespace().equals("minecraft")) {
            var sourceName = context.getSource().getName()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("\\s", "_");
            identifier = new Identifier(sourceName, givenIdentifier.getPath());
        } else {
            identifier = givenIdentifier;
        }

        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        if (workspaceManager.byId(identifier) != null) {
            throw MAP_ALREADY_EXISTS.create(identifier);
        }

        source.getServer().submit(() -> {
            try {
                if (worldConfig != null) {
                    workspaceManager.open(identifier, worldConfig);
                } else {
                    workspaceManager.open(identifier);
                }

                source.sendFeedback(
                        Text.translatable("text.nucleoid_creator_tools.map.open.success",
                                identifier,
                                Text.translatable("text.nucleoid_creator_tools.map.open.join_command", identifier).formatted(Formatting.GRAY)),
                        false
                );
            } catch (Throwable throwable) {
                source.sendError(Text.translatable("text.nucleoid_creator_tools.map.open.error"));
                CreatorTools.LOGGER.error("Failed to open workspace", throwable);
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int openWorkspaceLikeDimension(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var dimension = DimensionOptionsArgument.get(context, "dimension");
        var reg = context.getSource().getServer().getRegistryManager().get(RegistryKeys.DIMENSION_TYPE);
        var worldConfig = new RuntimeWorldConfig()
                .setDimensionType(reg.getEntry(reg.getKey(dimension.dimensionTypeEntry().value()).get()).get())
                .setGenerator(dimension.chunkGenerator());

        return MapManageCommand.openWorkspace(context, worldConfig);
    }

    private static int openWorkspaceByGenerator(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var generatorCodec = ChunkGeneratorArgument.get(context, "generator");
        var config = NbtCompoundArgumentType.getNbtCompound(context, "config");

        var server = context.getSource().getServer();
        var ops = RegistryOps.of(
                NbtOps.INSTANCE,
                server.getRegistryManager()
        );

        var result = generatorCodec.parse(ops, config);

        var error = result.error();
        if (error.isPresent()) {
            throw INVALID_GENERATOR_CONFIG.create(error.get());
        }

        var chunkGenerator = result.result().get();

        var worldConfig = new RuntimeWorldConfig()
                .setDimensionType(DimensionTypes.OVERWORLD)
                .setGenerator(chunkGenerator);
        return MapManageCommand.openWorkspace(context, worldConfig);
    }

    private static int setWorkspaceOrigin(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");
        var origin = BlockPosArgumentType.getBlockPos(context, "origin");

        workspace.setOrigin(origin);

        source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.map.origin.set"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int getWorkspaceBounds(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");
        var bounds = workspace.getBounds();

        source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.map.bounds.get", getClickablePosText(bounds.min()), getClickablePosText(bounds.max())), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int setWorkspaceBounds(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");
        var min = BlockPosArgumentType.getBlockPos(context, "min");
        var max = BlockPosArgumentType.getBlockPos(context, "max");

        workspace.setBounds(BlockBounds.of(min, max));

        source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.map.bounds.set"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int joinWorkspace(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        var workspace = MapWorkspaceArgument.get(context, "workspace");

        var workspaceWorld = workspace.getWorld();

        var returnPosition = WorkspaceTraveler.getReturnFor(player, workspaceWorld.getRegistryKey());
        if (returnPosition != null) {
            returnPosition.applyTo(player);
        } else {
            player.teleport(workspaceWorld, 0.0, 64.0, 0.0, 0.0F, 0.0F);
        }

        if (player.getAbilities().allowFlying) {
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        }

        source.sendFeedback(
                Text.translatable("text.nucleoid_creator_tools.map.join.success",
                        workspace.getIdentifier(),
                        Text.translatable("text.nucleoid_creator_tools.map.join.leave_command").formatted(Formatting.GRAY)),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int leaveMap(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        var workspace = workspaceManager.byDimension(player.world.getRegistryKey());

        if (workspace == null) {
            throw MAP_NOT_HERE.create();
        }

        var returnPosition = WorkspaceTraveler.getLeaveReturn(player);
        if (returnPosition != null) {
            returnPosition.applyTo(player);
        } else {
            var overworld = source.getServer().getOverworld();
            var spawnPos = overworld.getSpawnPos();
            player.teleport(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0.0F, 0.0F);
        }

        source.sendFeedback(
                Text.translatable("text.nucleoid_creator_tools.map.leave.success", workspace.getIdentifier()),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int exportMap(CommandContext<ServerCommandSource> context, boolean includeEntities) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");

        var template = workspace.compile(includeEntities);

        var bounds = template.getBounds();
        if (bounds.min().getY() < 0 || bounds.max().getY() > 255) {
            source.sendFeedback(
                    Text.translatable("text.nucleoid_creator_tools.map.export.vertical_bounds_warning.line.1").append("\n")
                            .append(Text.translatable("text.nucleoid_creator_tools.map.export.vertical_bounds_warning.line.2")).append("\n")
                            .append(Text.translatable("text.nucleoid_creator_tools.map.export.vertical_bounds_warning.line.3"))
                            .formatted(Formatting.YELLOW),
                    false
            );
        }

        var future = MapTemplateExporter.saveToExport(template, workspace.getIdentifier());

        future.handle((v, throwable) -> {
            if (throwable == null) {
                source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.map.export.success", workspace.getIdentifier()), false);
            } else {
                CreatorTools.LOGGER.error("Failed to export map to '{}'", workspace.getIdentifier(), throwable);
                source.sendError(Text.translatable("text.nucleoid_creator_tools.map.export.error"));
            }
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int deleteWorkspace(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace_once");
        var workspaceAgain = MapWorkspaceArgument.get(context, "workspace_again");
        if (workspace != workspaceAgain) {
            throw MAP_MISMATCH.create();
        }

        var workspaceManager = MapWorkspaceManager.get(source.getServer());

        MutableText message;
        if (workspaceManager.delete(workspace)) {
            message = Text.translatable("text.nucleoid_creator_tools.map.delete.success", workspace.getIdentifier());
        } else {
            message = Text.translatable("text.nucleoid_creator_tools.map.delete.error", workspace.getIdentifier());
        }

        source.sendFeedback(message.formatted(Formatting.RED), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int importWorkspace(CommandContext<ServerCommandSource> context, BlockPos origin) throws CommandSyntaxException {
        var source = context.getSource();
        var server = source.getServer();

        var location = IdentifierArgumentType.getIdentifier(context, "location");
        var toWorkspaceId = IdentifierArgumentType.getIdentifier(context, "to_workspace");

        var workspaceManager = MapWorkspaceManager.get(server);
        if (workspaceManager.byId(toWorkspaceId) != null) {
            throw MAP_ALREADY_EXISTS.create(toWorkspaceId);
        }

        var future = tryLoadTemplateForImport(server, location);

        future.thenAcceptAsync(template -> {
            if (template != null) {
                source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.map.import.importing"), false);

                var workspace = workspaceManager.open(toWorkspaceId);

                workspace.setBounds(template.getBounds().offset(origin));
                workspace.setOrigin(origin);

                for (var region : template.getMetadata().getRegions()) {
                    workspace.addRegion(region.getMarker(), region.getBounds().offset(origin), region.getData());
                }

                workspace.setData(template.getMetadata().getData());

                try {
                    var placer = new MapTemplatePlacer(template);
                    placer.placeAt(workspace.getWorld(), origin);
                    source.sendFeedback(Text.translatable("text.nucleoid_creator_tools.map.import.success", toWorkspaceId), false);
                } catch (Exception e) {
                    CreatorTools.LOGGER.error("Failed to place template into world!", e);
                }
            } else {
                source.sendError(Text.translatable("text.nucleoid_creator_tools.map.import.no_template_found", location));
            }
        }, server);

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<MapTemplate> tryLoadTemplateForImport(MinecraftServer server, Identifier location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return MapTemplateExporter.loadFromExport(location);
            } catch (IOException ignored) {
                try {
                    return MapTemplateSerializer.loadFromResource(server, location);
                } catch (IOException e) {
                    CreatorTools.LOGGER.error("Failed to import workspace at {}", location, e);
                    return null;
                }
            }
        }, Util.getIoWorkerExecutor());
    }

    protected static Text getClickablePosText(BlockPos pos) {
        var linkCommand = "/tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        var linkStyle = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, linkCommand))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip")))
                .withFormatting(Formatting.GREEN);

        return Texts.bracketed(Text.translatable("chat.coordinates", pos.getX(), pos.getY(), pos.getZ())).setStyle(linkStyle);
    }
}
