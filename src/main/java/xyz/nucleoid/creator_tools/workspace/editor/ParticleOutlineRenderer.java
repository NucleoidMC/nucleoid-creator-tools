package xyz.nucleoid.creator_tools.workspace.editor;

import org.joml.Vector3i;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

public final class ParticleOutlineRenderer {
    public static void render(ServerPlayerEntity player, BlockPos min, BlockPos max, float red, float green, float blue) {
        var effect = new DustParticleEffect(ColorHelper.fromFloats(1, red, green, blue), 2.0F);

        var edges = edges(min, max);

        int maxInterval = 5;
        int maxCount = 20;

        for (var edge : edges) {
            int length = edge.length();

            int steps = Math.max(Math.min(length, maxCount), (length + maxInterval - 1) / maxInterval);

            for (int i = 1; i < steps; i++) {
                double m = i / ((double) steps);
                spawnParticleIfVisible(
                        player, effect,
                        edge.projX(m), edge.projY(m), edge.projZ(m)
                );
            }
        }

        var vertices = vertices(min, max);

        for (var vertex : vertices) {
            spawnParticleIfVisible(player, effect, vertex.x, vertex.y, vertex.z);
        }
    }

    private static void spawnParticleIfVisible(ServerPlayerEntity player, ParticleEffect effect, double x, double y, double z) {
        var world = player.getServerWorld();

        var delta = player.getPos().subtract(x, y, z);
        double length2 = delta.lengthSquared();
        if (length2 > 256 * 256) {
            return;
        }

        var rotation = player.getRotationVec(1.0F);
        double dot = (delta.multiply(1.0 / Math.sqrt(length2))).dotProduct(rotation);
        if (dot > 0.0) {
            return;
        }

        world.spawnParticles(
                player, effect, false, true,
                x, y, z,
                1,
                0.0, 0.0, 0.0,
                0.0
        );
    }

    private static Vector3i[] vertices(BlockPos min, BlockPos max) {
        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();
        int maxX = max.getX() + 1;
        int maxY = max.getY() + 1;
        int maxZ = max.getZ() + 1;

        return new Vector3i[] {
                new Vector3i(minX, minY, minZ),
                new Vector3i(minX, minY, maxZ),
                new Vector3i(minX, maxY, minZ),
                new Vector3i(minX, maxY, maxZ),
                new Vector3i(maxX, minY, minZ),
                new Vector3i(maxX, minY, maxZ),
                new Vector3i(maxX, maxY, minZ),
                new Vector3i(maxX, maxY, maxZ)
        };
    }

    private static Edge[] edges(BlockPos min, BlockPos max) {
        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();
        int maxX = max.getX() + 1;
        int maxY = max.getY() + 1;
        int maxZ = max.getZ() + 1;

        return new Edge[] {
                // edges
                new Edge(minX, minY, minZ, minX, minY, maxZ),
                new Edge(minX, maxY, minZ, minX, maxY, maxZ),
                new Edge(maxX, minY, minZ, maxX, minY, maxZ),
                new Edge(maxX, maxY, minZ, maxX, maxY, maxZ),

                // front
                new Edge(minX, minY, minZ, minX, maxY, minZ),
                new Edge(maxX, minY, minZ, maxX, maxY, minZ),
                new Edge(minX, minY, minZ, maxX, minY, minZ),
                new Edge(minX, maxY, minZ, maxX, maxY, minZ),

                // back
                new Edge(minX, minY, maxZ, minX, maxY, maxZ),
                new Edge(maxX, minY, maxZ, maxX, maxY, maxZ),
                new Edge(minX, minY, maxZ, maxX, minY, maxZ),
                new Edge(minX, maxY, maxZ, maxX, maxY, maxZ),
        };
    }

    private record Edge(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        double projX(double m) {
            return this.startX + (this.endX - this.startX) * m;
        }

        double projY(double m) {
            return this.startY + (this.endY - this.startY) * m;
        }

        double projZ(double m) {
            return this.startZ + (this.endZ - this.startZ) * m;
        }

        int length() {
            int dx = this.endX - this.startX;
            int dy = this.endY - this.startY;
            int dz = this.endZ - this.startZ;
            return MathHelper.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
    }
}
