package xyz.nucleoid.creator_tools.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;

public record RegionVisibilityFilterComponent(List<String> regions) {
    public static final Codec<RegionVisibilityFilterComponent> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.listOf().fieldOf("regions").forGetter(RegionVisibilityFilterComponent::regions)
        ).apply(instance, RegionVisibilityFilterComponent::new);
    });

    public static final PacketCodec<ByteBuf, RegionVisibilityFilterComponent> PACKET_CODEC = PacketCodecs.STRING
            .collect(PacketCodecs.toList())
            .xmap(RegionVisibilityFilterComponent::new, RegionVisibilityFilterComponent::regions);
}
