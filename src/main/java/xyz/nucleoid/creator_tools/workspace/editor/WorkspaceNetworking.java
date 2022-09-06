package xyz.nucleoid.creator_tools.workspace.editor;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.workspace.WorkspaceRegion;
import xyz.nucleoid.map_templates.BlockBounds;

public final class WorkspaceNetworking {
    public static final int NO_PROTOCOL_VERSION = -1;

    // Client <-> Server
    public static final Identifier WORKSPACE_LEAVE_ID = new Identifier(CreatorTools.ID, "workspace/leave");
    public static final Identifier WORKSPACE_BOUNDS_ID = new Identifier(CreatorTools.ID, "workspace/bounds");
    public static final Identifier WORKSPACE_DATA_ID = new Identifier(CreatorTools.ID, "workspace/data");
    public static final Identifier WORKSPACE_REGION_ID = new Identifier(CreatorTools.ID, "workspace/region");
    public static final Identifier WORKSPACE_REGION_REMOVE_ID = new Identifier(CreatorTools.ID, "workspace/region/remove");

    // Client <-- Server
    public static final Identifier WORKSPACE_ENTER_ID = new Identifier(CreatorTools.ID, "workspace/enter");
    public static final Identifier WORKSPACE_REGIONS_ID = new Identifier(CreatorTools.ID, "workspace/regions");

    // Client --> Server
    public static final Identifier OPT_IN_ID = new Identifier(CreatorTools.ID, "opt_in");
    public static final Identifier WORKSPACE_NEW_ID = new Identifier(CreatorTools.ID, "workspace/new");
    public static final Identifier WORKSPACE_REGION_ADD_ID = new Identifier(CreatorTools.ID, "workspace/region/add");

    private WorkspaceNetworking() {
        return;
    }

    public static void writeBounds(PacketByteBuf buf, BlockBounds bounds) {
        buf.writeBlockPos(bounds.min());
        buf.writeBlockPos(bounds.max());
    }

    public static void writeRegion(PacketByteBuf buf, WorkspaceRegion region) {
        buf.writeString(region.marker());
        writeBounds(buf, region.bounds());
        buf.writeNbt(region.data());
    }
}
