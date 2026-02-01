package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import xyz.nucleoid.creator_tools.CreatorTools;

import java.util.function.Function;

public final class CreatorToolsItems {
    public static final Item ADD_REGION = register("add_region", AddRegionItem::new);
    public static final Item INCLUDE_ENTITY = register("include_entity", IncludeEntityItem::new);
    public static final Item REGION_VISIBILITY_FILTER = register("region_visibility_filter", RegionVisibilityFilterItem::new);

    public static final CreativeModeTab ITEM_GROUP = FabricItemGroup.builder()
        .title(Component.translatable("text.nucleoid_creator_tools.name"))
        .icon(ADD_REGION::getDefaultInstance)
        .displayItems((context, entries) -> {
            entries.accept(ADD_REGION);
            entries.accept(INCLUDE_ENTITY);
            entries.accept(REGION_VISIBILITY_FILTER);
        })
        .build();

    private static Item register(String path, Function<Item.Properties, Item> factory) {
        var id = CreatorTools.identifier(path);
        var key = ResourceKey.create(Registries.ITEM, id);

        var settings = new Item.Properties().setId(key);
        var item = factory.apply(settings);

        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void register() {
        PolymerItemGroupUtils.registerPolymerItemGroup(CreatorTools.identifier("general"), ITEM_GROUP);
    }
}
