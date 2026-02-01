package xyz.nucleoid.creator_tools.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Either;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.creator_tools.workspace.MapWorkspace;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MapMetadataCommand {
    public static final DynamicCommandExceptionType ENTITY_TYPE_NOT_FOUND = new DynamicCommandExceptionType(arg ->
            Component.translatableEscape("text.nucleoid_creator_tools.map.region.entity.filter.entity_type_not_found", arg)
    );

    public static final SimpleCommandExceptionType MAP_NOT_HERE = MapManageCommand.MAP_NOT_HERE;

    public static final SimpleCommandExceptionType NO_REGION_READY = new SimpleCommandExceptionType(
            Component.translatable("text.nucleoid_creator_tools.map.region.commit.no_region_ready")
    );

    private static final SimpleCommandExceptionType MERGE_FAILED_EXCEPTION = new SimpleCommandExceptionType(
            Component.translatable("commands.data.merge.failed")
    );

    private static final SimpleCommandExceptionType GET_MULTIPLE_EXCEPTION = new SimpleCommandExceptionType(
            Component.translatable("commands.data.get.multiple")
    );

    private static final DynamicCommandExceptionType MODIFY_EXPECTED_OBJECT_EXCEPTION = new DynamicCommandExceptionType(
            arg -> Component.translatableEscape("commands.data.modify.expected_object", arg)
    );

    public static final DynamicCommandExceptionType INVALID_REGION_SELECTOR = new DynamicCommandExceptionType(
            arg -> Component.translatableEscape("text.nucleoid_creator_tools.map.region.selector.invalid", arg)
    );

    public static final DynamicCommandExceptionType RESERVED_REGION_NAME = new DynamicCommandExceptionType(
            arg -> Component.translatableEscape("text.nucleoid_creator_tools.map.region.reserved_name", arg)
    );

    private enum SpecialRegionSelector {
        ALL("all");

        private final static String PREFIX = "+";
        private final String name;

        SpecialRegionSelector(String name) {
            this.name = name;
        }

        public static Stream<String> suggestions() {
            return Arrays.stream(values()).map(selector -> PREFIX + selector.name);
        }
    }

    private record RegionSelector(Either<String, SpecialRegionSelector> inner) {
        static RegionSelector special(SpecialRegionSelector selector) {
            return new RegionSelector(Either.right(selector));
        }

        static RegionSelector named(String name) {
            return new RegionSelector(Either.left(name));
        }

        public boolean matches(WorkspaceRegion region) {
            // This is to make sure we don't miss this if we add more special selectors:
            // noinspection ConstantValue
            return this.inner.map(
                    name -> region.marker().equals(name),
                    special -> switch (special) {
                        case ALL -> true;
                    }
            );
        }
    }

    /**
     * Get a region marker argument, throwing an error if the name is reserved.
     *
     * @throws CommandSyntaxException if the name is reserved
     */
    private static String getRegionMarkerArg(CommandContext<CommandSourceStack> ctx, String argName) throws CommandSyntaxException {
        var str = StringArgumentType.getString(ctx, argName);

        if (str.startsWith(SpecialRegionSelector.PREFIX)) {
            throw RESERVED_REGION_NAME.create(str);
        } else {
            return str;
        }
    }

    /**
     * Create a region marker argument. This is different to a region _selector_ argument, as it must be a valid name
     * (e.g. as used in commit or rename). It still suggests currently existing region names for convenience.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> regionMarkerArg(String argName) {
        return argument(argName, StringArgumentType.word()).suggests(globalRegionSuggestions(false));
    }

    /**
     * Create a region selector argument, which can either be a name or a special selector like +all.
     */
    @SuppressWarnings("SameParameterValue") // We want to keep this general
    private static RegionSelector getRegionSelectorArg(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        var str = StringArgumentType.getString(ctx, name);

        if (str.startsWith(SpecialRegionSelector.PREFIX)) {
            var selector = Arrays.stream(SpecialRegionSelector.values())
                    .filter(s -> s.name.equals(str.substring(1)))
                    .findAny()
                    .orElseThrow(() -> INVALID_REGION_SELECTOR.create(SpecialRegionSelector.PREFIX + str));
            return RegionSelector.special(selector);
        } else {
            return RegionSelector.named(str);
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> regionSelectorArg(
            String name,
            SuggestionProvider<CommandSourceStack> suggestions
    ) {
        return argument(name, StringArgumentType.word()).suggests(suggestions);
    }

    @SuppressWarnings("SameParameterValue") // We want to keep this general
    private static RequiredArgumentBuilder<CommandSourceStack, String> localRegionSelectorArg(String name) {
        return regionSelectorArg(name, localRegionSuggestions());
    }

    @SuppressWarnings("SameParameterValue") // We want to keep this general
    private static RequiredArgumentBuilder<CommandSourceStack, String> blockPosRegionSelectorArg(String name) {
        return regionSelectorArg(name, blockPosRegionSuggestions());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> globalRegionSelectorArg(String name) {
        return regionSelectorArg(name, globalRegionSuggestions(true));
    }

    // @formatter:off
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("map").requires(Permissions.require("nucleoid_creator_extras.map", PermissionLevel.GAMEMASTERS))
                .then(literal("region")
                    .then(literal("add")
                        .then(regionMarkerArg("marker")
                        .then(argument("min", BlockPosArgument.blockPos())
                        .then(argument("max", BlockPosArgument.blockPos())
                        .executes(MapMetadataCommand::addRegion)
                        .then(argument("data", CompoundTagArgument.compoundTag())
                        .executes(context -> addRegion(context, CompoundTagArgument.getCompoundTag(context, "data")))
                    )))))
                    .then(literal("rename")
                        .then(literal("all")
                            .then(globalRegionSelectorArg("old")
                            .then(regionMarkerArg("new")
                            .executes(context -> {
                                var old = getRegionSelectorArg(context, "old");
                                return renameRegions(
                                        context,
                                        getRegionMarkerArg(context, "new"),
                                        old::matches
                                );
                            })
                        )))
                        .then(literal("here")
                            .then(globalRegionSelectorArg("old")
                            .then(regionMarkerArg("new")
                            .executes(context -> {
                                var old = getRegionSelectorArg(context, "old");
                                var playerBounds = getPlayerBounds(context.getSource().getPlayerOrException());
                                return renameRegions(
                                        context,
                                        getRegionMarkerArg(context, "new"),
                                        (r) -> old.matches(r) && r.bounds().intersects(playerBounds)
                                );
                            })
                        )))
                    )
                    .then(literal("bounds")
                        .then(globalRegionSelectorArg("marker")
                        .executes(MapMetadataCommand::getRegionBounds))
                    )
                    .then(literal("data")
                        .then(localRegionSelectorArg("marker")
                            .then(literal("get").executes(executeInRegions("", MapMetadataCommand::executeRegionDataGet)))
                            .then(literal("merge")
                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                    .executes(executeInRegions("Merged data in %d regions.", MapMetadataCommand::executeRegionDataMerge))
                            ))
                            .then(literal("set")
                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                    .executes(executeInRegions("Set data in %d regions.", MapMetadataCommand::executeRegionDataSet))
                            ))
                            .then(literal("remove")
                                .then(argument("path", NbtPathArgument.nbtPath())
                                    .executes(executeInRegions("Removed data in %d regions.", MapMetadataCommand::executeRegionDataRemove))
                            ))
                    ))
                    .then(literal("remove")
                        .then(literal("here")
                            .then(localRegionSelectorArg("marker")
                            .executes(executeInRegions("Removed %d regions.", MapMetadataCommand::executeRemoveNamedRegionsHere))
                        ))
                        .then(literal("at")
                            .then(argument("pos", BlockPosArgument.blockPos())
                            .then(blockPosRegionSelectorArg("marker")
                            .executes(context -> {
                                final var pos = BlockPosArgument.getBlockPos(context, "pos");
                                final var selector = getRegionSelectorArg(context, "marker");
                                return removeRegions(context, r -> selector.matches(r) && r.bounds().contains(pos));
                            })
                        )))
                        .then(literal("all")
                            .then(globalRegionSelectorArg("marker")
                            .executes(context -> {
                                final var selector = getRegionSelectorArg(context, "marker");
                                return removeRegions(context, selector::matches);
                            })
                        ))
                    )
                    .then(literal("commit")
                        .then(regionMarkerArg("marker")
                        .executes(MapMetadataCommand::commitRegion)
                        .then(argument("data", CompoundTagArgument.compoundTag())
                        .executes(context -> commitRegion(context, CompoundTagArgument.getCompoundTag(context, "data")))
                    )))
                )
                .then(literal("entity")
                    .then(literal("add")
                        .then(argument("entities", EntityArgument.entities())
                        .executes(MapMetadataCommand::addEntities)
                    ))
                    .then(literal("remove")
                        .then(argument("entities", EntityArgument.entities())
                        .executes(MapMetadataCommand::removeEntities)
                    ))
                    .then(literal("filter")
                        .then(literal("type")
                            .then(literal("add")
                                .then(argument("entity_type", IdentifierArgument.id()).suggests(entityTypeSuggestions())
                                .executes(MapMetadataCommand::addEntityType)
                            ))
                            .then(literal("remove")
                                .then(argument("entity_type", IdentifierArgument.id()).suggests(entityTypeSuggestions())
                                .executes(MapMetadataCommand::removeEntityType)
                            ))
                        )
                    )
                )
                .then(literal("data")
                        .then(literal("get")
                            .executes(MapMetadataCommand::executeDataGet)
                            .then(literal("at")
                                .then(argument("path", NbtPathArgument.nbtPath())
                                .executes(MapMetadataCommand::executeDataGetAt)
                        )))
                        .then(literal("merge")
                            .then(argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(MapMetadataCommand::executeDataMerge)
                            )
                            .then(argument("nbt", NbtTagArgument.nbtTag())
                                .then(literal("at")
                                .then(argument("path", NbtPathArgument.nbtPath())
                                .executes(MapMetadataCommand::executeDataMergeAt)
                            )))
                        )
                        .then(literal("remove")
                            .executes(context -> executeDataRemove(context, null))
                            .then(literal("at")
                                .then(argument("path", NbtPathArgument.nbtPath())
                                .executes(context -> executeDataRemove(context, NbtPathArgument.getPath(context, "path")))
                        )))
                        .then(literal("set")
                            .then(argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(MapMetadataCommand::executeDataSet)
                            )
                            .then(literal("at")
                                .then(argument("path", NbtPathArgument.nbtPath())
                                    .then(argument("nbt", NbtTagArgument.nbtTag())
                                    .executes(MapMetadataCommand::executeDataSetAt)
                            )))
                        )
                )
        );
    }
    // @formatter:on

    private static int addRegion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return addRegion(context, new CompoundTag());
    }

    private static int addRegion(CommandContext<CommandSourceStack> context, CompoundTag data) throws CommandSyntaxException {
        var source = context.getSource();

        var marker = getRegionMarkerArg(context, "marker");
        var min = BlockPosArgument.getBlockPos(context, "min");
        var max = BlockPosArgument.getBlockPos(context, "max");

        var map = getWorkspaceForSource(source);
        map.addRegion(marker, BlockBounds.of(min, max), data);
        source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.region.add.success", marker)), false);

        return Command.SINGLE_SUCCESS;
    }

    private static BlockBounds getPlayerBounds(ServerPlayer player) {
        return BlockBounds.of(player.blockPosition(), player.blockPosition().offset(0, 1, 0));
    }

    private static int renameRegions(CommandContext<CommandSourceStack> context, String newMarker, Predicate<WorkspaceRegion> predicate) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(source);

        var regions = map.getRegions().stream()
                .filter(predicate)
                .toList();

        for (var region : regions) {
            map.removeRegion(region);
            map.addRegion(newMarker, region.bounds(), region.data());
        }

        source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.region.rename.success", regions.size())), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int getRegionBounds(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(source);

        var regionSelector = getRegionSelectorArg(context, "marker");
        var regions = map.getRegions().stream()
                .filter(regionSelector::matches)
                .toList();

        source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.region.bounds.get.header", regions.size()).withStyle(ChatFormatting.BOLD), false);

        for (var region : regions) {
            source.sendSuccess(() -> {
                var minText = MapManageCommand.getClickablePosText(region.bounds().min());
                var maxText = MapManageCommand.getClickablePosText(region.bounds().max());

                return Component.translatable("text.nucleoid_creator_tools.entry", Component.translatable("text.nucleoid_creator_tools.map.region.bounds.get", minText, maxText));
            }, false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static Component formatNbt(final Tag data) {
        return new TextComponentTagVisitor("  ").visit(data);
    }

    private static boolean executeRegionDataGet(CommandContext<CommandSourceStack> context, MapWorkspace map, WorkspaceRegion region) {
        context.getSource().sendSuccess(() -> withMapPrefix(map,
                Component.translatable("text.nucleoid_creator_tools.map.region.data.get", region.marker(), formatNbt(region.data()))
        ), false);
        return false;
    }

    private static boolean executeRegionDataMerge(CommandContext<CommandSourceStack> context, MapWorkspace map, WorkspaceRegion region) {
        var data = CompoundTagArgument.getCompoundTag(context, "nbt");
        map.replaceRegion(region, region.withData(region.data().copy().merge(data)));
        return true;
    }

    private static boolean executeRegionDataSet(CommandContext<CommandSourceStack> context, MapWorkspace map, WorkspaceRegion region) {
        var data = CompoundTagArgument.getCompoundTag(context, "nbt");
        map.replaceRegion(region, region.withData(data));
        return true;
    }

    private static boolean executeRegionDataRemove(CommandContext<CommandSourceStack> context, MapWorkspace map, WorkspaceRegion region) {
        var path = NbtPathArgument.getPath(context, "path");
        return path.remove(region.data()) > 0;
    }

    private static boolean executeRemoveNamedRegionsHere(CommandContext<CommandSourceStack> context, MapWorkspace map, WorkspaceRegion region) {
        return map.removeRegion(region);
    }

    private static int removeRegions(CommandContext<CommandSourceStack> context, Predicate<WorkspaceRegion> predicate) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(source);

        var regions = map.getRegions().stream()
                .filter(predicate)
                .toList();

        for (var region : regions) {
            map.removeRegion(region);
        }

        source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.region.remove.success", regions.size())), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int commitRegion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return commitRegion(context, new CompoundTag());
    }

    private static int commitRegion(CommandContext<CommandSourceStack> context, CompoundTag data) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        var marker = getRegionMarkerArg(context, "marker");

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
            source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.region.add.success.excited", marker), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int addEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var world = source.getLevel();

        var map = getWorkspaceForSource(source);

        long result = EntityArgument.getEntities(context, "entities").stream()
                .filter(entity -> entity.level() == world && !(entity instanceof Player)
                        && map.getBounds().contains(entity.blockPosition()))
                .filter(entity -> map.addEntity(entity.getUUID()))
                .count();

        if (result == 0) {
            source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.region.entity.add.error", Component.translationArg(map.getIdentifier())));
        } else {
            source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.region.entity.add.success", result, Component.translationArg(map.getIdentifier())),
                    false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int removeEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var world = source.getLevel();

        var map = getWorkspaceForSource(source);

        long result = EntityArgument.getEntities(context, "entities").stream()
                .filter(entity -> entity.level() == world && !(entity instanceof Player))
                .filter(entity -> map.removeEntity(entity.getUUID()))
                .count();

        if (result == 0) {
            source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.region.entity.remove.error", Component.translationArg(map.getIdentifier())));
        } else {
            source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.region.entity.remove.success", result, Component.translationArg(map.getIdentifier())),
                    false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int addEntityType(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();

        var map = getWorkspaceForSource(source);
        var type = getEntityType(context);

        if (!map.addEntityType(type.getB())) {
            source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.add.already_present", Component.translationArg(type.getA()), Component.translationArg(map.getIdentifier())));
        } else {
            source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.add.success", Component.translationArg(type.getA()), Component.translationArg(map.getIdentifier())), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeEntityType(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();

        var map = getWorkspaceForSource(source);
        var type = getEntityType(context);

        if (!map.removeEntityType(type.getB())) {
            source.sendFailure(Component.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.remove.not_present", Component.translationArg(type.getA()), Component.translationArg(map.getIdentifier())));
        } else {
            source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.region.entity.filter.type.remove.success", Component.translationArg(type.getA()), Component.translationArg(map.getIdentifier())), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataMerge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var data = CompoundTagArgument.getCompoundTag(context, "nbt");
        var originalData = map.getData();
        map.setData(originalData.copy().merge(data));
        source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.data.merge.success")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataMergeAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());

        var sourceData = CompoundTagArgument.getCompoundTag(context, "nbt");
        var path = NbtPathArgument.getPath(context, "path");

        var sourceElements = path.getOrCreate(sourceData, CompoundTag::new);
        var mergeIntoElements = path.get(map.getData());

        int mergeCount = 0;

        for (var mergeIntoTag : mergeIntoElements) {
            if (!(mergeIntoTag instanceof CompoundTag mergedCompound)) {
                throw MODIFY_EXPECTED_OBJECT_EXCEPTION.create(mergeIntoTag);
            }

            var previousCompound = mergedCompound.copy();

            for (var sourceElement : sourceElements) {
                if (!(sourceElement instanceof CompoundTag sourceCompound)) {
                    throw MODIFY_EXPECTED_OBJECT_EXCEPTION.create(sourceElement);
                }

                mergedCompound.merge(sourceCompound);
            }

            if (!previousCompound.equals(mergedCompound)) {
                mergeCount++;
            }
        }

        if (mergeCount == 0) {
            throw MERGE_FAILED_EXCEPTION.create();
        }

        map.setData(map.getData());
        source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.data.merge.success")), false);

        return mergeCount;
    }

    private static int executeDataGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.data.get",
                        getMapPrefix(map), formatNbt(map.getData())),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataGetAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var path = NbtPathArgument.getPath(context, "path");
        var element = getTagAt(map.getData(), path);
        source.sendSuccess(() -> Component.translatable("text.nucleoid_creator_tools.map.data.get.at",
                        Component.translationArg(map.getIdentifier()), path.toString(),
                        formatNbt(element)),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataRemove(CommandContext<CommandSourceStack> context, @Nullable NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        if (path == null) {
            map.setData(new CompoundTag());
            source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.data.remove.success")),
                    false);
        } else {
            int count = path.remove(map.getData());
            if (count == 0) {
                throw MERGE_FAILED_EXCEPTION.create();
            } else {
                source.sendSuccess(() -> withMapPrefix(map,
                        Component.translatable("text.nucleoid_creator_tools.map.data.remove.at.success", path.toString())),
                        false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var data = CompoundTagArgument.getCompoundTag(context, "nbt");
        map.setData(data);
        source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.data.set.success")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDataSetAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var map = getWorkspaceForSource(context.getSource());
        var path = NbtPathArgument.getPath(context, "path");
        var element = NbtTagArgument.getNbtTag(context, "nbt");
        var data = map.getData().copy();
        if (path.set(data, element.copy()) == 0) {
            throw MERGE_FAILED_EXCEPTION.create();
        } else {
            map.setData(data);
            source.sendSuccess(() -> withMapPrefix(map, Component.translatable("text.nucleoid_creator_tools.map.data.set.at.success",
                            path.toString())),
                    false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Tag getTagAt(CompoundTag data, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        Collection<Tag> collection = path.get(data);
        var iterator = collection.iterator();
        var tag = iterator.next();
        if (iterator.hasNext()) {
            throw GET_MULTIPLE_EXCEPTION.create();
        } else {
            return tag;
        }
    }

    private static Tuple<Identifier, EntityType<?>> getEntityType(CommandContext<CommandSourceStack> context) throws
            CommandSyntaxException {
        var id = IdentifierArgument.getId(context, "entity_type");
        return new Tuple<>(id, BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElseThrow(() -> ENTITY_TYPE_NOT_FOUND.create(id)));
    }

    private static SuggestionProvider<CommandSourceStack> entityTypeSuggestions() {
        return (ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder);
    }

    private static SuggestionProvider<CommandSourceStack> globalRegionSuggestions(boolean includeSpecial) {
        return (context, builder) -> {
            var map = getWorkspaceForSource(context.getSource());
            var regions = map.getRegions().stream().map(WorkspaceRegion::marker);
            return SharedSuggestionProvider.suggest(
                    includeSpecial ? Stream.concat(SpecialRegionSelector.suggestions(), regions) : regions,
                    builder
            );
        };
    }

    private static Stream<String> getRegionMarkersAtBlockPos(MapWorkspace workspace, BlockPos pos) {
        return workspace.getRegions().stream().filter(region -> region.bounds().contains(pos)).map(WorkspaceRegion::marker);
    }

    private static SuggestionProvider<CommandSourceStack> blockPosRegionSuggestions() {
        return (context, builder) -> {
            var map = getWorkspaceForSource(context.getSource());
            var pos = BlockPosArgument.getBlockPos(context, "pos");
            var localRegions = getRegionMarkersAtBlockPos(map, pos);
            return SharedSuggestionProvider.suggest(
                    Stream.concat(SpecialRegionSelector.suggestions(), localRegions),
                    builder
            );
        };
    }

    private static SuggestionProvider<CommandSourceStack> localRegionSuggestions() {
        return (context, builder) -> {
            var map = getWorkspaceForSource(context.getSource());
            var sourcePos = context.getSource().getPlayerOrException().blockPosition();
            var localRegions = getRegionMarkersAtBlockPos(map, sourcePos);
            return SharedSuggestionProvider.suggest(
                    Stream.concat(SpecialRegionSelector.suggestions(), localRegions),
                    builder
            );
        };
    }

    private static @NotNull MapWorkspace getWorkspaceForSource(CommandSourceStack source) throws CommandSyntaxException {
        var workspaceManager = MapWorkspaceManager.get(source.getServer());
        var workspace = workspaceManager.byDimension(source.getLevel().dimension());
        if (workspace == null) {
            throw MAP_NOT_HERE.create();
        }

        return workspace;
    }

    private static Command<CommandSourceStack> executeInRegions(String message, RegionExecutor executor) {
        return context -> {
            var source = context.getSource();
            var playerBounds = getPlayerBounds(source.getPlayerOrException());
            var regionSelector = getRegionSelectorArg(context, "marker");

            var map = getWorkspaceForSource(context.getSource());
            var regions = map.getRegions().stream()
                    .filter(region -> region.bounds().intersects(playerBounds))
                    .filter(regionSelector::matches)
                    .toList();

            int count = 0;
            for (var region : regions) {
                if (executor.execute(context, map, region)) { count++; }
            }

            if (count > 0) {
                int finalCount = count;
                source.sendSuccess(() -> withMapPrefix(map, Component.literal(String.format(message, finalCount))), false);
            }
            return 2;
        };
    }

    private static Component getMapPrefix(MapWorkspace map) {
        return withMapPrefix(map, null);
    }

    private static Component withMapPrefix(MapWorkspace map, @Nullable Component text) {
        var prefix = Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(map.getIdentifier().toString()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
        if (text != null) prefix.append(text);
        return prefix;
    }

    @FunctionalInterface
    private interface RegionExecutor {
        boolean execute(CommandContext<CommandSourceStack> context, MapWorkspace map, WorkspaceRegion region);
    }
}
