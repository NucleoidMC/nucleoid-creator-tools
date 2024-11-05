package xyz.nucleoid.creator_tools.component;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import xyz.nucleoid.creator_tools.CreatorTools;

public final class CreatorToolsDataComponentTypes {
    public static final ComponentType<RegionVisibilityFilterComponent> REGION_VISIBILITY_FILTER = register("region_visibility_filter", ComponentType.<RegionVisibilityFilterComponent>builder()
            .codec(RegionVisibilityFilterComponent.CODEC)
            .packetCodec(RegionVisibilityFilterComponent.PACKET_CODEC)
            .cache()
            .build());

    private static <T> ComponentType<T> register(String path, ComponentType<T> type) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, CreatorTools.identifier(path), type);
    }

    public static void register() {
        PolymerComponent.registerDataComponent(REGION_VISIBILITY_FILTER);
    }
}
