package xyz.nucleoid.creator_tools.item;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import xyz.nucleoid.creator_tools.CreatorTools;

import java.util.function.Function;

public final class CreatorToolsItems {
    public static final Item ADD_REGION = register("add_region", AddRegionItem::new);
    public static final Item INCLUDE_ENTITY = register("include_entity", IncludeEntityItem::new);
    public static final Item REGION_VISIBILITY_FILTER = register("region_visibility_filter", RegionVisibilityFilterItem::new);

    public static final ItemGroup ITEM_GROUP = FabricItemGroup.builder()
        .displayName(Text.translatable("text.nucleoid_creator_tools.name"))
        .icon(ADD_REGION::getDefaultStack)
        .entries((context, entries) -> {
            entries.add(ADD_REGION);
            entries.add(INCLUDE_ENTITY);
            entries.add(REGION_VISIBILITY_FILTER);
        })
        .build();

    private static Item register(String path, Function<Item.Settings, Item> factory) {
        var id = CreatorTools.identifier(path);
        var key = RegistryKey.of(RegistryKeys.ITEM, id);

        var settings = new Item.Settings().registryKey(key);
        var item = factory.apply(settings);

        return Registry.register(Registries.ITEM, key, item);
    }

    public static void register() {
        PolymerItemGroupUtils.registerPolymerItemGroup(CreatorTools.identifier("general"), ITEM_GROUP);
    }
}
