package xyz.nucleoid.creator_tools.item;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import xyz.nucleoid.creator_tools.CreatorTools;

public final class CreatorToolsItems {
    public static final Item ADD_REGION = register("add_region", new AddRegionItem(new Item.Settings()));
    public static final Item INCLUDE_ENTITY = register("include_entity", new IncludeEntityItem(new Item.Settings()));
    public static final Item REGION_VISIBILITY_FILTER = register("region_visibility_filter", new RegionVisibilityFilterItem(new Item.Settings()));

    private static Item register(String identifier, Item item) {
        return Registry.register(Registry.ITEM, new Identifier(CreatorTools.ID, identifier), item);
    }
}
