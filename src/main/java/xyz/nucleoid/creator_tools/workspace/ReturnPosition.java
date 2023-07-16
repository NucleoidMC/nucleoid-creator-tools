package xyz.nucleoid.creator_tools.workspace;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record ReturnPosition(RegistryKey<World> dimension, Vec3d position, float yaw, float pitch) {
    public static ReturnPosition capture(PlayerEntity player) {
        return new ReturnPosition(player.getWorld().getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch());
    }

    public void applyTo(ServerPlayerEntity player) {
        var world = player.getServer().getWorld(this.dimension);
        player.teleport(world, this.position.x, this.position.y, this.position.z, this.yaw, this.pitch);
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
        var dimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(root.getString("dimension")));
        double x = root.getDouble("x");
        double y = root.getDouble("y");
        double z = root.getDouble("z");
        float yaw = root.getFloat("yaw");
        float pitch = root.getFloat("pitch");
        return new ReturnPosition(dimension, new Vec3d(x, y, z), yaw, pitch);
    }
}
