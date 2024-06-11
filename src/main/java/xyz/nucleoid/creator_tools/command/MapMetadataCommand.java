package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.NbtElementArgumentType;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Collection;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MapMetadataCommand {
    public static final DynamicCommandExceptionType ENTITY_TYPE_NOT_FOUND = new DynamicCommandExceptionType(arg ->
            Text.stringifiedTranslatable("text.nucleoid_creator_tools.map.region.entity.filter.entity_type_not_found", arg)
    );

    public static final SimpleCommandExceptionType MAP_NOT_HERE = MapManageCommand.MAP_NOT_HERE;

    public static final SimpleCommandExceptionType NO_REGION_READY = new SimpleCommandExceptionType(
            Text.translatable("text.nucleoid_creator_tools.map.region.commit.no_region_ready")
    );

    private static final SimpleCommandExceptionType MERGE_FAILED_EXCEPTION = new SimpleCommandExceptionType(
            Text.translatable("commands.data.merge.failed")
    );

    private static final SimpleCommandExceptionType GET_MULTIPLE_EXCEPTION = new SimpleCommandExceptionType(
            Text.translatable("commands.data.get.multiple")
    );

    private static final DynamicCommandExceptionType MODIFY_EXPECTED_OBJECT_EXCEPTION = new DynamicCommandExceptionType(
            arg -> Text.stringifiedTranslatable("commands.data.modify.expected_object", arg)
    );

    // @formatter:off
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("map").requires(Permissions.require("nucleoid_creator_extras.map", 2))
                .then(literal("region")
                    .then(literal("add")
                        .then(argument("marker", StringArgumentType.word())
                        .then(argument("min", BlockPosArgumentType.blockPos())
                        .then(argument("max", BlockPosArgumentType.blockPos())
                        .executes(MapMetadataCommand::addRegion)
                        .then(argument("data", NbtCompoundArgumentType.nbtCompound())
                        .executes(context -> addRegion(context, NbtCompoundArgumentType.getNbtCompound(context, "data")))
                    )))))
                    .then(literal("rename")
                        .then(literal("all")
                            .then(argument("old", StringArgumentType.word()).suggests(regionSuggestions())
                            .then(argument("new", StringArgumentType.word())
                            .executes(context -> {
                                var oldMarker = StringArgumentType.getString(context, "old");
                                return renameRegions(
                                        context,
                                        StringArgumentType.getString(context, "new"),
                                        (r) -> r.marker().equals(oldMarker)
                                );
                            })
                        )))
                        .then(literal("here")
                            .then(argument("old", StringArgumentType.word()).suggests(localRegionSuggestions())
                            .then(argument("new", StringArgumentType.word())
                            .executes(context -> {
                                var oldMarker = StringArgumentType.getString(context, "old");
                                var playerBounds = getPlayerBounds(context.getSource().getPlayerOrThrow());
                                return renameRegions(
                                        context,
                                        StringArgumentType.getString(context, "new"),
                                        (r) -> r.marker().equals(oldMarker) && r.bounds().intersects(playerBounds)
                                );
                            })
                        )))
                    )
                    .then(literal("bounds")
                        .then(argument("marker", StringArgumentType.word()).suggests(regionSuggestions())
                        .executes(MapMetadataCommand::getRegionBounds))
                    )
                    .then(literal("data")
                        .then(argument("marker", StringArgumentType.word()).suggests(localRegionSuggestions())
                            .then(literal("get").executes(executeInRegions("", MapMetadataCommand::executeRegionDataGet)))
                            .then(literal("merge")
                                .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                    .executes(executeInRegions("Merged data in %d regions.", MapMetadataCommand::executeRegionDataMerge))
                            ))
                            .then(literal("set")
                                .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                    .executes(executeInRegions("Set data in %d regions.", MapMetadataCommand::executeRegionDataSet))
                            ))
                            .then(literal("remove")
                                .then(argument("path", NbtPathArgumentType.nbtPath())
                                    .executes(executeInRegions("Removed data in %d regions.", MapMetadataCommand::executeRegionDataRemove))
                            ))
                    ))
                    .then(literal("remove")
                        .then(literal("here")
                            .executes(MapMetadataCommand::removeRegionHere)
                        )
                        .then(literal("at")
                            .then(argument("pos", BlockPosArgumentType.blockPos())
                            .executes(MapMetadataCommand::removeRegionAt)
                        ))
                        .then(literal("all")
                            .then(argument("marker", StringArgumentType.word()).suggests(regionSuggestions())
                            .executes(context -> {
                                final var marker = StringArgumentType.getString(context, "marker");
                                return removeRegions(context, r -> r.marker().equals(marker));
                            })
                        ))
                    )
                    .then(literal("commit")
                        .then(argument("marker", StringArgumentType.word())
                        .executes(MapMetadataCommand::commitRegion)
                        .then(argument("data", NbtCompoundArgumentType.nbtCompound())
                        .executes(context -> commitRegion(context, NbtCompoundArgumentType.getNbtCompound(context, "data")))
                    )))
                )
                .then(literal("entity")
                    .then(literal("add")
                        .then(argument("entities", EntityArgumentType.entities())
                        .executes(MapMetadataCommand::addEntities)
                    ))
                    .then(literal("remove")
                        .then(argument("entities", EntityArgumentType.entities())
                        .executes(MapMetadataCommand::removeEntities)
                    ))
                    .then(literal("filter")
                        .then(literal("type")
                            .then(literal("add")
                                .then(argument("entity_type", IdentifierArgumentType.identifier()).suggests(entityTypeSuggestions())
                                .executes(MapMetadataCommand::addEntityType)
                            ))
                            .then(literal("remove")
                                .then(argument("entity_type", IdentifierArgumentType.identifier()).suggests(entityTypeSuggestions())
                                .executes(MapMetadataCommand::removeEntityType)
                            ))
                        )
                    )
                )
                .then(literal("data")
                        .then(literal("get")
                            .executes(MapMetadataCommand::executeDataGet)
                            .then(literal("at")
                                .then(argument("path", NbtPathArgumentType.nbtPath())
                                .executes(MapMetadataCommand::executeDataGetAt)
                        )))
                        .then(literal("merge")
                            .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                .executes(MapMetadataCommand::executeDataMerge)
                            )
                            .then(argument("nbt", NbtElementArgumentType.nbtElement())
                                .then(literal("at")
                                .then(argument("path", NbtPathArgumentType.nbtPath())
                                .executes(MapMetadataCommand::executeDataMergeAt)
                            )))
                        )
                        .then(literal("remove")
                            .executes(context -> executeDataRemove(context, null))
                            .then(literal("at")
                                .then(argument("path", NbtPathArgumentType.nbtPath())
                                .executes(context -> executeDataRemove(context, NbtPathArgumentType.getNbtPath(context, "path")))
                        )))
                        .then(literal("set")
                            .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                .executes(MapMetadataCommand::executeDataSet)
                            )
                            .then(literal("at")
                                .then(argument("path", NbtPathArgumentType.nbtPath())
                                    .then(argument("nbt", NbtElementArgumentType.nbtElement())
                                    .executes(MapMetadataCommand::executeDataSetAt)
                            )))
                        )
                )
        );
    }
    // @formatter:on

    private static int addRegion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return addRegion(context, new NbtCompound());
    }

    private static int addRegion(CommandContext<ServerCommandSource> context, NbtCompound data) throws CommandSyntaxException {
        var source = context.getSource();

        var marker = StringArgumentType.getString(context, "marker");
        var min = BlockPosArgumentType.getBlockPos(context, "min");
        var max = BlockPosArgumentType.getBlockPos(context, "max");

        var map = getWorkspaceForSource(source);
        map.addRegion(marker, BlockBounds.of(min, max), data);
        source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.region.add.success", marker)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static BlockBounds getPlayerBounds(ServerPlayerEntity player) {
        return BlockBounds.of(player.getBlockPos(), player.getBlockPos().add(0, 1, 0));
    }

    private static int renameRegions(CommandContext<ServerCommandSource> context, String newMarker, Predicate<WorkspaceRegion> predicate) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(source);

        var regions = map.getRegions().stream()
                .filter(predicate)
                .toList();

        for (var region : regions) {
            map.removeRegion(region);
            map.addRegion(newMarker, region.bounds(), region.data());
        }

        source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.region.rename.success", regions.size())), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int getRegionBounds(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(source);

        var marker = StringArgumentType.getString(context, "marker");

        var regions = map.getRegions().stream()
                .filter(region -> region.marker().equals(marker))
                .toList();

        source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.region.bounds.get.header", regions.size()).formatted(Formatting.BOLD), false);

        for (var region : regions) {
            source.sendFeedback(() -> {
                var minText = MapManageCommand.getClickablePosText(region.bounds().min());
                var maxText = MapManageCommand.getClickablePosText(region.bounds().max());

                return Text.translatable("text.nucleoid_creator_tools.entry", Text.translatable("text.nucleoid_creator_tools.map.region.bounds.get", minText, maxText));
            }, false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static Text formatNbt(final NbtElement data) {
        return new NbtTextFormatter("  ", 0).apply(data);
    }

    private static boolean executeRegionDataGet(CommandContext<ServerCommandSource> context, MapWorkspace map, WorkspaceRegion region) {
        context.getSource().sendFeedback(() -> withMapPrefix(map,
                Text.translatable("text.nucleoid_creator_tools.map.region.data.get", region.marker(), formatNbt(region.data()))
        ), false);
        return false;
    }

    private static boolean executeRegionDataMerge(CommandContext<ServerCommandSource> context, MapWorkspace map, WorkspaceRegion region) {
        var data = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        map.replaceRegion(region, region.withData(region.data().copy().copyFrom(data)));
        return true;
    }

    private static boolean executeRegionDataSet(CommandContext<ServerCommandSource> context, MapWorkspace map, WorkspaceRegion region) {
        var data = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        map.replaceRegion(region, region.withData(data));
        return true;
    }

    private static boolean executeRegionDataRemove(CommandContext<ServerCommandSource> context, MapWorkspace map, WorkspaceRegion region) {
        var path = NbtPathArgumentType.getNbtPath(context, "path");
        return path.remove(region.data()) > 0;
    }

    private static int removeRegionHere(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        final var playerBounds = getPlayerBounds(context.getSource().getPlayerOrThrow());
        return removeRegions(context, region -> region.bounds().intersects(playerBounds));
    }

    private static int removeRegionAt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return removeRegions(context, region -> region.bounds().contains(BlockPosArgumentType.getBlockPos(context, "pos")));
    }

    private static int removeRegions(CommandContext<ServerCommandSource> context, Predicate<WorkspaceRegion> predicate) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(source);

        var regions = map.getRegions().stream()
                .filter(predicate)
                .toList();

        for (var region : regions) {
            map.removeRegion(region);
        }

        source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.region.remove.success", regions.size())), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int commitRegion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return commitRegion(context, new NbtCompound());
    }

    private static int commitRegion(CommandContext<ServerCommandSource> context, NbtCompound data) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        var marker = StringArgumentType.getString(context, "marker");

        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        var editor = workspaceManager.getEditorFor(player);
        if (editor != null) {
            var region = editor.takeTracedRegion();
            if (region == null) {
                throw NO_REGION_READY.create();
            }

            var min = region.min();
            var max = region.max();

            var workspace = getWorkspaceForSource(source);
            workspace.addRegion(marker, BlockBounds.of(min, max), data);
            source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.region.add.success.excited", marker), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int addEntities(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var world = source.getWorld();

        var map = getWorkspaceForSource(source);

        long result = EntityArgumentType.getEntities(context, "entities").stream()
                .filter(entity -> entity.getEntityWorld().equals(world) && !(entity instanceof PlayerEntity)
                        && map.getBounds().contains(entity.getBlockPos()))
                .filter(entity -> map.addEntity(entity.getUuid()))
                .count();

        if (result == 0) {
            source.sendError(Text.translatable("text.nucleoid_creator_tools.map.region.entity.add.error", Text.of(map.getIdentifier())));
        } else {
            source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.region.entity.add.success", result, Text.of(map.getIdentifier())),
                    false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int removeEntities(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var world = source.getWorld();

        var map = getWorkspaceForSource(source);

        long result = EntityArgumentType.getEntities(context, "entities").stream()
                .filter(entity -> entity.getEntityWorld().equals(world) && !(entity instanceof PlayerEntity))
                .filter(entity -> map.removeEntity(entity.getUuid()))
                .count();

        if (result == 0) {
            source.sendError(Text.translatable("text.nucleoid_creator_tools.map.region.entity.remove.error", Text.of(map.getIdentifier())));
        } else {
            source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.region.entity.remove.success", result, Text.of(map.getIdentifier())),
                    false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int addEntityType(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();

        var map = getWorkspaceForSource(source);
        var type = getEntityType(context);

        if (!map.addEntityType(type.getRight())) {
            source.sendError(Text.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.add.already_present", Text.of(type.getLeft()), Text.of(map.getIdentifier())));
        } else {
            source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.add.success", Text.of(type.getLeft()), Text.of(map.getIdentifier())), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeEntityType(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();

        var map = getWorkspaceForSource(source);
        var type = getEntityType(context);

        if (!map.removeEntityType(type.getRight())) {
            source.sendError(Text.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.remove.not_present", Text.of(type.getLeft()), Text.of(map.getIdentifier())));
        } else {
            source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.remove.success", Text.of(type.getLeft()), Text.of(map.getIdentifier())), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataMerge(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var data = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        var originalData = map.getData();
        map.setData(originalData.copy().copyFrom(data));
        source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.data.merge.success")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataMergeAt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());

        var sourceData = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        var path = NbtPathArgumentType.getNbtPath(context, "path");

        var sourceElements = path.getOrInit(sourceData, NbtCompound::new);
        var mergeIntoElements = path.get(map.getData());

        int mergeCount = 0;

        for (var mergeIntoTag : mergeIntoElements) {
            if (!(mergeIntoTag instanceof NbtCompound mergedCompound)) {
                throw MODIFY_EXPECTED_OBJECT_EXCEPTION.create(mergeIntoTag);
            }

            var previousCompound = mergedCompound.copy();

            for (var sourceElement : sourceElements) {
                if (!(sourceElement instanceof NbtCompound sourceCompound)) {
                    throw MODIFY_EXPECTED_OBJECT_EXCEPTION.create(sourceElement);
                }

                mergedCompound.copyFrom(sourceCompound);
            }

            if (!previousCompound.equals(mergedCompound)) {
                mergeCount++;
            }
        }

        if (mergeCount == 0) {
            throw MERGE_FAILED_EXCEPTION.create();
        }

        map.setData(map.getData());
        source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.data.merge.success")), false);

        return mergeCount;
    }

    private static int executeDataGet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.data.get",
                        getMapPrefix(map), formatNbt(map.getData())),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataGetAt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var path = NbtPathArgumentType.getNbtPath(context, "path");
        var element = getTagAt(map.getData(), path);
        source.sendFeedback(() -> Text.translatable("text.nucleoid_creator_tools.map.data.get.at",
                        Text.of(map.getIdentifier()), path.toString(),
                        formatNbt(element)),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataRemove(CommandContext<ServerCommandSource> context, @Nullable NbtPathArgumentType.NbtPath path) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        if (path == null) {
            map.setData(new NbtCompound());
            source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.data.remove.success")),
                    false);
        } else {
            int count = path.remove(map.getData());
            if (count == 0) {
                throw MERGE_FAILED_EXCEPTION.create();
            } else {
                source.sendFeedback(() -> withMapPrefix(map,
                        Text.translatable("text.nucleoid_creator_tools.map.data.remove.at.success", path.toString())),
                        false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var data = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        map.setData(data);
        source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.data.set.success")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataSetAt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var path = NbtPathArgumentType.getNbtPath(context, "path");
        var element = NbtElementArgumentType.getNbtElement(context, "nbt");
        var data = map.getData().copy();
        if (path.put(data, element.copy()) == 0) {
            throw MERGE_FAILED_EXCEPTION.create();
        } else {
            map.setData(data);
            source.sendFeedback(() -> withMapPrefix(map, Text.translatable("text.nucleoid_creator_tools.map.data.set.at.success",
                            path.toString())),
                    false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static NbtElement getTagAt(NbtCompound data, NbtPathArgumentType.NbtPath path) throws CommandSyntaxException {
        Collection<NbtElement> collection = path.get(data);
        var iterator = collection.iterator();
        var tag = iterator.next();
        if (iterator.hasNext()) {
            throw GET_MULTIPLE_EXCEPTION.create();
        } else {
            return tag;
        }
    }

    private static Pair<Identifier, EntityType<?>> getEntityType(CommandContext<ServerCommandSource> context) throws
            CommandSyntaxException {
        var id = IdentifierArgumentType.getIdentifier(context, "entity_type");
        return new Pair<>(id, Registries.ENTITY_TYPE.getOrEmpty(id).orElseThrow(() -> ENTITY_TYPE_NOT_FOUND.create(id)));
    }

    private static SuggestionProvider<ServerCommandSource> entityTypeSuggestions() {
        return (ctx, builder) -> CommandSource.suggestIdentifiers(Registries.ENTITY_TYPE.getIds(), builder);
    }

    private static SuggestionProvider<ServerCommandSource> regionSuggestions() {
        return (context, builder) -> {
            var map = getWorkspaceForSource(context.getSource());
            return CommandSource.suggestMatching(
                    map.getRegions().stream().map(WorkspaceRegion::marker),
                    builder
            );
        };
    }

    private static SuggestionProvider<ServerCommandSource> localRegionSuggestions() {
        return (context, builder) -> {
            var map = getWorkspaceForSource(context.getSource());
            var sourceBounds = getPlayerBounds(context.getSource().getPlayerOrThrow());
            return CommandSource.suggestMatching(
                    map.getRegions().stream().filter(region -> region.bounds().intersects(sourceBounds))
                            .map(WorkspaceRegion::marker),
                    builder
            );
        };
    }

    private static @NotNull MapWorkspace getWorkspaceForSource(ServerCommandSource source) throws CommandSyntaxException {
        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        var workspace = workspaceManager.byDimension(source.getWorld().getRegistryKey());
        if (workspace == null) {
            throw MAP_NOT_HERE.create();
        }

        return workspace;
    }

    private static Command<ServerCommandSource> executeInRegions(String message, RegionExecutor executor) {
        return context -> {
            var source = context.getSource();
            var playerBounds = getPlayerBounds(source.getPlayerOrThrow());

            var marker = StringArgumentType.getString(context, "marker");

            var map = getWorkspaceForSource(context.getSource());
            var regions = map.getRegions().stream()
                    .filter(region -> region.bounds().intersects(playerBounds) && region.marker().equals(marker))
                    .toList();

            int count = 0;
            for (var region : regions) {
                if (executor.execute(context, map, region)) { count++; }
            }

            if (count > 0) {
                int finalCount = count;
                source.sendFeedback(() -> withMapPrefix(map, Text.literal(String.format(message, finalCount))), false);
            }
            return 2;
        };
    }

    private static Text getMapPrefix(MapWorkspace map) {
        return withMapPrefix(map, null);
    }

    private static Text withMapPrefix(MapWorkspace map, @Nullable Text text) {
        var prefix = Text.empty()
                .append(Text.literal("[").formatted(Formatting.GRAY))
                .append(Text.literal(map.getIdentifier().toString()).formatted(Formatting.GOLD))
                .append(Text.literal("] ").formatted(Formatting.GRAY));
        if (text != null) prefix.append(text);
        return prefix;
    }

    @FunctionalInterface
    private interface RegionExecutor {
        boolean execute(CommandContext<ServerCommandSource> context, MapWorkspace map, WorkspaceRegion region);
    }
}
