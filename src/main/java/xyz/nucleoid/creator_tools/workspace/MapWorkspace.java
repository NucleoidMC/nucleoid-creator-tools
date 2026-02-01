package xyz.nucleoid.creator_tools.workspace;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

import java.util.*;

/**
 * A map workspace represents an in-world map template within a dimension before it has been compiled to a static file.
 * <p>
 * It stores regions and arbitrary data destined to be compiled into a {@link MapTemplate}.
 */
public final class MapWorkspace {
    private final RuntimeWorldHandle worldHandle;

    private final Identifier identifier;

    private BlockPos origin = BlockPos.ZERO;
    private BlockBounds bounds;

    /* Regions */
    private final Int2ObjectMap<WorkspaceRegion> regions = new Int2ObjectOpenHashMap<>();

    /* Entities */
    private final Set<UUID> entitiesToInclude = new ObjectOpenHashSet<>();
    private final Set<EntityType<?>> entityTypesToInclude = new ObjectOpenHashSet<>();

    /* Data */
    private CompoundTag data = new CompoundTag();

    private int nextRegionId;

    private final List<WorkspaceListener> listeners = new ArrayList<>();

    public MapWorkspace(RuntimeWorldHandle worldHandle, Identifier identifier, BlockBounds bounds) {
        this.worldHandle = worldHandle;
        this.identifier = identifier;
        this.bounds = bounds;
    }

    public void addListener(WorkspaceListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(WorkspaceListener listener) {
        this.listeners.remove(listener);
    }

    private int nextRegionId() {
        return this.nextRegionId++;
    }

    public void addRegion(String marker, BlockBounds bounds, CompoundTag tag) {
        int runtimeId = this.nextRegionId();
        var region = new WorkspaceRegion(runtimeId, marker, bounds, tag);
        this.regions.put(runtimeId, region);

        for (var listener : this.listeners) {
            listener.onAddRegion(region);
        }
    }

    public boolean replaceRegion(WorkspaceRegion from, WorkspaceRegion to) {
        if (from.runtimeId() != to.runtimeId()) {
            throw new IllegalArgumentException("mismatched region runtime ids!");
        }

        if (this.regions.replace(from.runtimeId(), from, to)) {
            for (var listener : this.listeners) {
                listener.onUpdateRegion(from, to);
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean removeRegion(WorkspaceRegion region) {
        if (this.regions.remove(region.runtimeId(), region)) {
            for (var listener : this.listeners) {
                listener.onRemoveRegion(region);
            }
            return true;
        } else {
            return false;
        }
    }

    public Identifier getIdentifier() {
        return this.identifier;
    }

    public void setBounds(BlockBounds bounds) {
        this.bounds = bounds;

        for (var listener : this.listeners) {
            listener.onSetBounds(bounds);
        }
    }

    public void setOrigin(BlockPos origin) {
        this.origin = origin;

        for (var listener : this.listeners) {
            listener.onSetOrigin(origin);
        }
    }

    public BlockBounds getBounds() {
        return this.bounds;
    }

    public BlockPos getOrigin() {
        return this.origin;
    }

    public Collection<WorkspaceRegion> getRegions() {
        return this.regions.values();
    }

    public boolean addEntity(UUID entity) {
        return this.entitiesToInclude.add(entity);
    }

    public boolean containsEntity(UUID entity) {
        return this.entitiesToInclude.contains(entity);
    }

    public boolean removeEntity(UUID entity) {
        return this.entitiesToInclude.remove(entity);
    }

    public boolean addEntityType(EntityType<?> type) {
        return this.entityTypesToInclude.add(type);
    }

    public boolean hasEntityType(EntityType<?> type) {
        return this.entityTypesToInclude.contains(type);
    }

    public boolean removeEntityType(EntityType<?> type) {
        return this.entityTypesToInclude.remove(type);
    }

    /**
     * Gets the arbitrary data of the map.
     *
     * @return the data as a compound tag
     */
    public CompoundTag getData() {
        return this.data;
    }

    /**
     * Sets the arbitrary data of the map.
     *
     * @param data the data as a compound tag
     */
    public void setData(CompoundTag data) {
        this.data = data;

        for (var listener : this.listeners) {
            listener.onSetData(data);
        }
    }

    public CompoundTag serialize(CompoundTag root) {
        root.putString("identifier", this.identifier.toString());
        this.bounds.serialize(root);

        root.store("origin", BlockPos.CODEC, this.origin);

        // Regions
        var regionList = new ListTag();
        for (var region : this.regions.values()) {
            regionList.add(region.serialize(new CompoundTag()));
        }
        root.put("regions", regionList);

        // Entities
        var entitiesTag = new CompoundTag();

        entitiesTag.store("uuids", UUIDUtil.CODEC.listOf(), this.entitiesToInclude.stream().toList());
        entitiesTag.store("types", BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf(), this.entityTypesToInclude.stream().toList());

        root.put("entities", entitiesTag);

        // Data
        root.put("data", this.getData());

        return root;
    }

    public static MapWorkspace deserialize(RuntimeWorldHandle worldHandle, CompoundTag root) {
        var identifier = Identifier.parse(root.getStringOr("identifier", ""));
        var bounds = BlockBounds.deserialize(root);

        var map = new MapWorkspace(worldHandle, identifier, bounds);

        map.setOrigin(root.read("origin", BlockPos.CODEC).orElse(bounds.min()));

        // Regions
        var regionList = root.getListOrEmpty("regions");
        for (int i = 0; i < regionList.size(); i++) {
            var regionRoot = regionList.getCompoundOrEmpty(i);
            int runtimeId = map.nextRegionId();
            map.regions.put(runtimeId, WorkspaceRegion.deserialize(runtimeId, regionRoot));
        }

        // Entities
        var entitiesTag = root.getCompoundOrEmpty("entities");

        entitiesTag.read("entities", UUIDUtil.CODEC.listOf()).ifPresent(map.entitiesToInclude::addAll);

        entitiesTag.read("types", BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf()).ifPresent(map.entityTypesToInclude::addAll);

        // Data
        map.data = root.getCompoundOrEmpty("data");

        return map;
    }

    /**
     * Compiles this map workspace into a map template.
     * <p>
     * It copies the block and entity data from the world and stores it within the template.
     * All positions are made relative.
     *
     * @param includeEntities True if entities should be included, else false.
     * @return The compiled map.
     */
    public MapTemplate compile(boolean includeEntities) {
        var map = MapTemplate.createEmpty();
        map.setBounds(this.globalToLocal(this.bounds));

        this.writeMetadataToTemplate(map);

        var level = this.worldHandle.asWorld();

        this.writeBlocksToTemplate(map, level);

        if (includeEntities) {
            this.writeEntitiesToTemplate(map, level);
        }

        return map;
    }

    private void writeMetadataToTemplate(MapTemplate map) {
        var metadata = map.getMetadata();
        metadata.setData(this.getData().copy());

        for (var region : this.regions.values()) {
            metadata.addRegion(
                    region.marker(),
                    this.globalToLocal(region.bounds()),
                    region.data()
            );
        }
    }

    private void writeBlocksToTemplate(MapTemplate map, ServerLevel level) {
        for (var pos : this.bounds) {
            var localPos = this.globalToLocal(pos);

            var state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            map.setBlockState(localPos, state);

            var entity = level.getBlockEntity(pos);
            if (entity != null) {
                map.setBlockEntity(localPos, entity, level.registryAccess());
            }
        }
    }

    private void writeEntitiesToTemplate(MapTemplate map, ServerLevel level) {
        var entities = level.getEntitiesOfClass(Entity.class, this.bounds.asBox(), entity -> {
            if (entity.isRemoved()) {
                return false;
            }
            return this.containsEntity(entity.getUUID()) || this.hasEntityType(entity.getType());
        });

        for (var entity : entities) {
            map.addEntity(entity, this.globalToLocal(entity.position()));
        }
    }

    private BlockPos globalToLocal(BlockPos pos) {
        return pos.subtract(this.origin);
    }

    private Vec3 globalToLocal(Vec3 pos) {
        var origin = this.origin;
        return pos.subtract(origin.getX(), origin.getY(), origin.getZ());
    }

    private BlockBounds globalToLocal(BlockBounds bounds) {
        return BlockBounds.of(this.globalToLocal(bounds.min()), this.globalToLocal(bounds.max()));
    }

    public ServerLevel getLevel() {
        return this.worldHandle.asWorld();
    }

    RuntimeWorldHandle getWorldHandle() {
        return this.worldHandle;
    }
}
