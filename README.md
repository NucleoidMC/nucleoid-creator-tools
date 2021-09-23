# Nucleoid Creator Tools
This mod implements various tools for generating game content for the Nucleoid ecosystem.
The main current functionality with this mod is the handling of game map creation.

## Creating a Map
Any minigame needs a map for the game to take place within. While sometimes a minigame may want to generate the map procedurally, in many cases, hand-built maps are desirable.

The first step to creating a map is opening a map workspace. A map workspace is essentially just a dimension that you can build within, which can later be exported into a map template file readable by Plasmid.

An empty void map workspace can be opened by running: `/map open <id>` (e.g. `/map open bedwars:cubes`)  
The required `id` is a unique identifier for your workspace in `namespace:identifier` format. It should be all lowercase and cannot have spaces.

You are also able to create workspaces with custom chunk generators: `/map open <id> like <dimension>` (e.g. `/map open bedwars:nether like minecraft:nether`)

### Moving in and out of workspaces
Once you have created a workspace, you need to be able travel between them.

This is possible by running: `/map join <id>`, which will teleport you into that workspace dimension.
Once you are in the workspace dimension, you are free to build whatever you want!

Likewise, it is possible to leave a map workspace and return to your former location by running: `/map leave`.

### Setting the map bounds
When exporting a map to be loaded into a game, the mod needs to know the area of the world which should be included. This is controlled by setting a box that encompasses your map.

When you first enter a workspace, you will notice a box outline formed by particles- these are your map bounds! They can be changed in a workspace by running: `/map bounds <id> <corner_1> <corner_2>`.
  - `id` is the workspace id to set bounds for
  - `corner_1` and `corner_2` are the two corners of the axis-aligned bounding box

### Setting the map origin
More often than not, you will not need to change the origin of a map workspace. Essentially, though, the map origin controls the block position that will correspond to (0; 0; 0) once the map is exported. This is useful if you built your map in the wrong place, and want to move it when the game actually starts.

For example, if your map is 10 blocks too high, setting your origin to `(0; 10; 0)` will result in the exported map moving 10 blocks downward.

This can be set through `/map origin <id> <origin>`
  - `id` is the workspace id to set the origin for
  - `origin` is the block position to set the origin to

Be careful when setting origin to not cause your map to go out of bounds! If you set your map origin somewhere below the lower y of your map bounds, that will mean in the exported map, your bounds will be going below y=0!

### Working with regions
Having a map that we can load for a minigame is useful, but the game logic is missing any sort of useful information about the map. For example, how can your minigame know where to spawn players, or where the bed for a specific team in bedwars is located?

This is where regions come in: a region is just a named area of the map which can be used to communicate to the game code an area in which something should happen. There can be multiple regions in the map with the same name, or multiple regions with different names in the same location. The naming of regions is useful in order to identify to the game code what each region represents.

#### Creating a region
To get started with creating a region, run: `/give @s nucleoid_creator_tools:add_region`. This gives you the _Add Region_ item, which can be used to easily define regions within your world by selecting two corners.

By right clicking on a block, a particle box should appear. This starts the process of defining a region by selecting the first corner. Now, as you look around, the box will shape to match the second corner. Right clicking a second time will select the current looked-at block as the second corner.

Now, you can run `/map region commit <marker>`. This will add the highlighted region to your map with the given `marker`, and it should highlight with differently colored particles.

When selecting a region with the _Add Region_ item, you can additionally change the selection mode by sneaking & right clicking. This cycles through 3 modes:
  - offset mode: the highlighted block is the block you are looking at, offset by the side you are looking at it from (like placing a block)
  - exact mode: the highlighted block is the exact block your are looking at (like breaking a block)
  - at feet mode: the highlighted block is the block at your feet

#### Useful region commands
 - `/map region rename all <old> <new>`: renames all regions in the current workspace from `old` to `new`
 - `/map region rename here <old> <new>`: renames all regions intersecting with your player in the current workspace from `old` to `new`
 - `/map region remove here`:  removes all regions intersecting with your player from the current workspace
 - `/map region remove at <pos>`: removes all regions intersecting with the given `pos` from the current workspace

### Attaching data
It may desirable to communicate more information about the map to the code than just regions. This can be done by attaching arbitrary NBT data which is later accessible by the code.

Data can be either attached to a region or to the map as a whole.

To work with data on a specific region, your player **must be intersecting that region's bounds**!
  - `/map region data <marker> get`: prints the data for the given region
  - `/map region data <marker> set <data>`: sets the data for the given region (**this will overwrite any previously existing data!**)
  - `/map region data <marker> merge <data>`: merges the given data with the existing data on the given region

It is additionally possible to pass data to the region commit command: `/map region commit <marker> <data>`

To work with data on the global map, the commands function similarly:
  - `/map data get`: prints the data attached to the map
  - `/map data set <data>`: overwrites the existing data on the map
  - `/map data merge <data>`: merges the given data with the existing data on the map

### Exporting maps
Once your map is complete, you will want to export it into a file that can be loaded by a minigame mod. This can be done simply by running: `/map export <id>`.

The exported map will be placed in `/nucleoid_creator_tools/exports/<namespace>/map_templates/<path>.nbt`
