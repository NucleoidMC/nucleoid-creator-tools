package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

public record ReturnPosition(RegistryKey<World> dimension, Vec3d position, float yaw, float pitch) {
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

    public NbtCompound write(NbtCompound root) {
        root.putString("dimension", this.dimension.getValue().toString());
        root.putDouble("x", this.position.x);
        root.putDouble("y", this.position.y);
        root.putDouble("z", this.position.z);
        root.putFloat("yaw", this.yaw);
        root.putFloat("pitch", this.pitch);
        return root;
    }

    public static ReturnPosition read(NbtCompound root) {
        var dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(root.getString("dimension")));
        double x = root.getDouble("x");
        double y = root.getDouble("y");
        double z = root.getDouble("z");
        float yaw = root.getFloat("yaw");
        float pitch = root.getFloat("pitch");
        return new ReturnPosition(dimension, new Vec3d(x, y, z), yaw, pitch);
    }
}
