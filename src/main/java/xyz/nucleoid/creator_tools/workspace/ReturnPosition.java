package xyz.nucleoid.creator_tools.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public record ReturnPosition(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
    private static final Codec<ResourceKey<Level>> KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);

    public static final Codec<ReturnPosition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KEY_CODEC.fieldOf("dimension").forGetter(ReturnPosition::dimension),
            Codec.DOUBLE.optionalFieldOf("x", 0d).forGetter(pos -> pos.position.x),
            Codec.DOUBLE.optionalFieldOf("y", 0d).forGetter(pos -> pos.position.y),
            Codec.DOUBLE.optionalFieldOf("z", 0d).forGetter(pos -> pos.position.z),
            Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(pos -> pos.yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(pos -> pos.pitch)
    ).apply(instance, ReturnPosition::new));

    public static final Codec<Map<ResourceKey<Level>, ReturnPosition>> MAP_CODEC = Codec.unboundedMap(KEY_CODEC, CODEC);

    private ReturnPosition(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
        this(dimension, new Vec3(x, y, z), yaw, pitch);
    }

    public static ReturnPosition capture(Player player) {
        return new ReturnPosition(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
    }

    public static ReturnPosition ofSpawn(ServerLevel level) {

        var spawnPos = level.getRespawnData().pos();
        return new ReturnPosition(level.dimension(), Vec3.atBottomCenterOf(spawnPos), 0.0F, 0.0F);
    }

    public void applyTo(ServerPlayer player) {
        var world = player.level().getServer().getLevel(this.dimension);
        player.teleport(new TeleportTransition(world, this.position, Vec3.ZERO, this.yaw, this.pitch, TeleportTransition.DO_NOTHING));
    }
}
