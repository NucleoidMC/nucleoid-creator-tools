package xyz.nucleoid.creator_tools.component;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import xyz.nucleoid.creator_tools.CreatorTools;

public final class CreatorToolsDataComponentTypes {
    public static final DataComponentType<RegionVisibilityFilterComponent> REGION_VISIBILITY_FILTER = register("region_visibility_filter", DataComponentType.<RegionVisibilityFilterComponent>builder()
            .persistent(RegionVisibilityFilterComponent.CODEC)
            .networkSynchronized(RegionVisibilityFilterComponent.PACKET_CODEC)
            .cacheEncoding()
            .build());

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatorTools.identifier(path), type);
    }

    public static void register() {
        PolymerComponent.registerDataComponent(REGION_VISIBILITY_FILTER);
    }
}
