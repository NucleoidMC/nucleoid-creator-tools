package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.MapTemplateExporter;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.ReturnPosition;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MapManageCommand {
    public static final SimpleCommandExceptionType MAP_NOT_HERE = new SimpleCommandExceptionType(
            Component.translatable("text.nucleoid_creator_tools.map.map_not_here")
    );

    public static final DynamicCommandExceptionType MAP_ALREADY_EXISTS = new DynamicCommandExceptionType(arg ->
            Component.translatableEscape("text.nucleoid_creator_tools.map.open.map_already_exists", arg)
    );

    public static final SimpleCommandExceptionType MAP_MISMATCH = new SimpleCommandExceptionType(
            Component.translatable("text.nucleoid_creator_tools.map.delete.map_mismatch")
    );

    public static final DynamicCommandExceptionType INVALID_GENERATOR_CONFIG = new DynamicCommandExceptionType(arg ->
            Component.translatableEscape("text.nucleoid_creator_tools.map.open.invalid_generator_config", arg)
    );

    // @formatter:off
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("map").requires(Permissions.require("nucleoid_creator_extras.map", PermissionLevel.GAMEMASTERS))
                .then(literal("open")
                    .then(argument("workspace", IdentifierArgument.id())
                    .executes(context -> MapManageCommand.openWorkspace(context, null))
                        .then(literal("like")
                            .then(DimensionOptionsArgument.argument("dimension")
                            .executes(MapManageCommand::openWorkspaceLikeDimension)
                        ))
                        .then(literal("with")
                            .then(ChunkGeneratorArgument.argument("generator")
                            .then(argument("config", CompoundTagArgument.compoundTag())
                            .executes(MapManageCommand::openWorkspaceByGenerator)
                        )))
                ))
                .then(literal("origin")
                    .then(MapWorkspaceArgument.argument("workspace")
                    .then(argument("origin", BlockPosArgument.blockPos())
                    .executes(MapManageCommand::setWorkspaceOrigin)
                )))
                .then(literal("bounds")
                    .then(MapWorkspaceArgument.argument("workspace")
                        .executes(MapManageCommand::getWorkspaceBounds)
                        .then(argument("min", BlockPosArgument.blockPos())
                            .then(argument("max", BlockPosArgument.blockPos())
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
                    .then(argument("location", IdentifierArgument.id())
                    .then(argument("to_workspace", IdentifierArgument.id())
                        .then(argument("origin", BlockPosArgument.blockPos())
                            .executes(context -> {
                                BlockPos origin = BlockPosArgument.getBlockPos(context, "origin");
                                return MapManageCommand.importWorkspace(context, origin);
                            })
                        )
                    .executes(context -> MapManageCommand.importWorkspace(context, BlockPos.ZERO))
                )))
        );
    }
    // @formatter:on

    private static int openWorkspace(CommandContext<CommandSourceStack> context, RuntimeWorldConfig worldConfig) throws CommandSyntaxException {
        var source = context.getSource();

        var givenIdentifier = IdentifierArgument.getId(context, "workspace");

        Identifier identifier;
        if (givenIdentifier.getNamespace().equals("minecraft")) {
            var sourceName = context.getSource().getTextName()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("\\s", "_");
            identifier = Identifier.fromNamespaceAndPath(sourceName, givenIdentifier.getPath());
        } else {
            identifier = givenIdentifier;
        }

        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        if (workspaceManager.byId(identifier) != null) {
            throw MAP_ALREADY_EXISTS.create(identifier);
        }

        try {
            if (worldConfig != null) {
                workspaceManager.open(identifier, worldConfig);
            } else {
                workspaceManager.open(identifier);
            }

            source.sendSuccess(
                    () -> Component.translatable("text.nucleoid_creator_tools.map.open.success",
                            Component.translationArg(identifier),
                            Component.translatable("text.nucleoid_creator_tools.map.open.join_command", Component.translationArg(identifier)).withStyle(style ->
                                    style.withColor(ChatFormatting.GREEN)
                                            .withClickEvent(new ClickEvent.SuggestCommand("/map join " + identifier)))),
                    false
            );
        } catch (Throwable throwable) {
            source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.open.error"));
            CreatorTools.LOGGER.error("Failed to open workspace", throwable);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int openWorkspaceLikeDimension(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var dimension = DimensionOptionsArgument.get(context, "dimension");
        var reg = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
        var worldConfig = new RuntimeWorldConfig()
                .setDimensionType(reg.getOrThrow(reg.getResourceKey(dimension.type().value()).get()))
                .setGenerator(dimension.generator());

        return MapManageCommand.openWorkspace(context, worldConfig);
    }

    private static int openWorkspaceByGenerator(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var generatorCodec = ChunkGeneratorArgument.get(context, "generator");
        var config = CompoundTagArgument.getCompoundTag(context, "config");

        var server = context.getSource().getServer();
        var ops = RegistryOps.create(
                NbtOps.INSTANCE,
                server.registryAccess()
        );

        var chunkGenerator = generatorCodec.codec().parse(ops, config).getOrThrow(INVALID_GENERATOR_CONFIG::create);

        var worldConfig = new RuntimeWorldConfig()
                .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                .setGenerator(chunkGenerator);
        return MapManageCommand.openWorkspace(context, worldConfig);
    }

    private static int setWorkspaceOrigin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");
        var origin = BlockPosArgument.getBlockPos(context, "origin");

        workspace.setOrigin(origin);

        source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.origin.set"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int getWorkspaceBounds(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");
        var bounds = workspace.getBounds();

        source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.bounds.get", getClickablePosText(bounds.min()), getClickablePosText(bounds.max())), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int setWorkspaceBounds(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");
        var min = BlockPosArgument.getBlockPos(context, "min");
        var max = BlockPosArgument.getBlockPos(context, "max");

        workspace.setBounds(BlockBounds.of(min, max));

        source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.bounds.set"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int joinWorkspace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();

        var workspace = MapWorkspaceArgument.get(context, "workspace");

        var workspaceWorld = workspace.getLevel();

        var returnPosition = WorkspaceTraveler.getReturnFor(player, workspaceWorld.dimension());
        if (returnPosition != null) {
            returnPosition.applyTo(player);
        } else {
            player.teleport(new TeleportTransition(workspaceWorld, new Vec3(0.0, 64.0, 0.0), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
        }

        if (player.getAbilities().mayfly) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }

        source.sendSuccess(
                () -> Component.translatable("text.nucleoid_creator_tools.map.join.success",
                        Component.translationArg(workspace.getIdentifier()),
                        Component.literal("/map leave").withStyle(style ->
                                style.withColor(ChatFormatting.GREEN)
                                        .withClickEvent(new ClickEvent.SuggestCommand("/map leave")))),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int leaveMap(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();

        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        var workspace = workspaceManager.byDimension(player.level().dimension());

        if (workspace == null) {
            throw MAP_NOT_HERE.create();
        }

        var returnPosition = WorkspaceTraveler.getLeaveReturn(player);
        if (returnPosition != null) {
            returnPosition.applyTo(player);
        } else {
            var overworld = source.getServer().overworld();
            ReturnPosition.ofSpawn(overworld).applyTo(player);
        }

        source.sendSuccess(
                () -> Component.translatable("text.nucleoid_creator_tools.map.leave.success", Component.translationArg(workspace.getIdentifier())),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int exportMap(CommandContext<CommandSourceStack> context, boolean includeEntities) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace");

        var template = workspace.compile(includeEntities);

        var bounds = template.getBounds();
        if (bounds.min().getY() < 0 || bounds.max().getY() > 255) {
            source.sendSuccess(
                    () -> Component.translatable("text.nucleoid_creator_tools.map.export.vertical_bounds_warning.line.1").append("\n")
                            .append(Component.translatable("text.nucleoid_creator_tools.map.export.vertical_bounds_warning.line.2")).append("\n")
                            .append(Component.translatable("text.nucleoid_creator_tools.map.export.vertical_bounds_warning.line.3"))
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        var registries = source.registryAccess();
        var future = MapTemplateExporter.saveToExport(template, workspace.getIdentifier(), registries);

        future.handle((v, throwable) -> {
            if (throwable == null) {
                source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.export.success", Component.translationArg(workspace.getIdentifier())), false);
            } else {
                CreatorTools.LOGGER.error("Failed to export map to '{}'", workspace.getIdentifier(), throwable);
                source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.export.error"));
            }
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int deleteWorkspace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();

        var workspace = MapWorkspaceArgument.get(context, "workspace_once");
        var workspaceAgain = MapWorkspaceArgument.get(context, "workspace_again");
        if (workspace != workspaceAgain) {
            throw MAP_MISMATCH.create();
        }

        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        boolean deleted = workspaceManager.delete(workspace);

        source.sendSuccess(() -> {
            MutableComponent message;
            if (deleted) {
                message = Component.translatable("text.nucleoid_creator_tools.map.delete.success", Component.translationArg(workspace.getIdentifier()));
            } else {
                message = Component.translatable("text.nucleoid_creator_tools.map.delete.error", Component.translationArg(workspace.getIdentifier()));
            }

            return message.withStyle(ChatFormatting.RED);
        }, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int importWorkspace(CommandContext<CommandSourceStack> context, BlockPos origin) throws CommandSyntaxException {
        var source = context.getSource();
        var server = source.getServer();

        var location = IdentifierArgument.getId(context, "location");
        var toWorkspaceId = IdentifierArgument.getId(context, "to_workspace");

        var workspaceManager = MapWorkspaceManager.get(server);
        if (workspaceManager.byId(toWorkspaceId) != null) {
            throw MAP_ALREADY_EXISTS.create(toWorkspaceId);
        }

        var future = tryLoadTemplateForImport(server, location);

        future.thenAcceptAsync(template -> {
            if (template != null) {
                source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.import.importing"), false);

                var workspace = workspaceManager.open(toWorkspaceId);

                workspace.setBounds(template.getBounds().offset(origin));
                workspace.setOrigin(origin);

                for (var region : template.getMetadata().getRegions()) {
                    workspace.addRegion(region.getMarker(), region.getBounds().offset(origin), region.getData());
                }

                workspace.setData(template.getMetadata().getData());

                try {
                    var placer = new MapTemplatePlacer(template);
                    placer.placeAt(workspace.getLevel(), origin);
                    source.sendSuccess(() ->
                                    Component.translatable("text.nucleoid_creator_tools.map.import.success",
                                            Component.translationArg(toWorkspaceId).copy().withStyle(style -> style
                                                    .withColor(ChatFormatting.GREEN)
                                                    .withClickEvent(new ClickEvent.SuggestCommand("/map join " + toWorkspaceId)))),
                            false);
                } catch (Exception e) {
                    CreatorTools.LOGGER.error("Failed to place template into world!", e);
                }
            } else {
                source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.import.no_template_found", Component.translationArg(location)));
            }
        }, server);

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<MapTemplate> tryLoadTemplateForImport(MinecraftServer server, Identifier location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return MapTemplateExporter.loadFromExport(location, server.registryAccess());
            } catch (IOException ignored) {
                try {
                    return MapTemplateSerializer.loadFromResource(server, location);
                } catch (IOException e) {
                    CreatorTools.LOGGER.error("Failed to import workspace at {}", location, e);
                    return null;
                }
            }
        }, Util.ioPool());
    }

    static Component getClickablePosText(BlockPos pos) {
        var linkCommand = "/tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        var linkStyle = Style.EMPTY
                .withClickEvent(new ClickEvent.SuggestCommand(linkCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip")))
                .applyFormat(ChatFormatting.GREEN);

        return ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", pos.getX(), pos.getY(), pos.getZ())).setStyle(linkStyle);
    }
}
