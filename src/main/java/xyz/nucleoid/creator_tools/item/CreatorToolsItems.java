package xyz.nucleoid.creator_tools.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.CreatorTools;

public final class CreatorToolsItems {
    public static final Item ADD_REGION = register("add_region", new AddRegionItem(new Item.Settings()));
    public static final Item INCLUDE_ENTITY = register("include_entity", new IncludeEntityItem(new Item.Settings()));

    private static Item register(String identifier, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(CreatorTools.ID, identifier), item);
    }
}
