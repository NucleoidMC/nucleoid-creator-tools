package xyz.nucleoid.creator_tools.workspace;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

public record ReturnPosition(RegistryKey<World> dimension, Vec3d position, float yaw, float pitch) {
    private static final Codec<RegistryKey<World>> KEY_CODEC = RegistryKey.createCodec(RegistryKeys.WORLD);

    public static final Codec<ReturnPosition> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                KEY_CODEC.fieldOf("dimension").forGetter(ReturnPosition::dimension),
                Codec.DOUBLE.optionalFieldOf("x", 0d).forGetter(pos -> pos.position.x),
                Codec.DOUBLE.optionalFieldOf("y", 0d).forGetter(pos -> pos.position.y),
                Codec.DOUBLE.optionalFieldOf("z", 0d).forGetter(pos -> pos.position.z),
                Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(pos -> pos.yaw),
                Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(pos -> pos.pitch)
        ).apply(instance, ReturnPosition::new);
    });

    public static final Codec<Map<RegistryKey<World>, ReturnPosition>> MAP_CODEC = Codec.unboundedMap(KEY_CODEC, CODEC);

    private ReturnPosition(RegistryKey<World> dimension, double x, double y, double z, float yaw, float pitch) {
        this(dimension, new Vec3d(x, y, z), yaw, pitch);
    }

    public static ReturnPosition capture(PlayerEntity player) {
        return new ReturnPosition(player.getWorld().getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch());
    }

    public static ReturnPosition ofSpawn(ServerWorld world) {
        var spawnPos = world.getSpawnPos();
        return new ReturnPosition(world.getRegistryKey(), Vec3d.ofBottomCenter(spawnPos), 0.0F, 0.0F);
    }

    public void applyTo(ServerPlayerEntity player) {
        var world = player.getServer().getWorld(this.dimension);
        player.teleportTo(new TeleportTarget(world, this.position, Vec3d.ZERO, this.yaw, this.pitch, TeleportTarget.NO_OP));
    }
}
