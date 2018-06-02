/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.core.asm.mixin.core.common;

import static io.github.opencubicchunks.cubicchunks.api.util.Coords.blockToCube;
import static io.github.opencubicchunks.cubicchunks.api.util.Coords.blockToLocal;

import com.google.common.base.Predicate;
import io.github.opencubicchunks.cubicchunks.core.world.ClientHeightMap;
import io.github.opencubicchunks.cubicchunks.core.world.ServerHeightMap;
import io.github.opencubicchunks.cubicchunks.core.world.column.ColumnTileEntityMap;
import io.github.opencubicchunks.cubicchunks.core.world.column.CubeMap;
import io.github.opencubicchunks.cubicchunks.core.world.cube.BlankCube;
import io.github.opencubicchunks.cubicchunks.core.world.cube.Cube;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.core.CubicChunks;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.core.world.ClientHeightMap;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.IHeightMap;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.ICubicWorldInternal;
import io.github.opencubicchunks.cubicchunks.core.world.ServerHeightMap;
import io.github.opencubicchunks.cubicchunks.core.world.column.ColumnTileEntityMap;
import io.github.opencubicchunks.cubicchunks.core.world.column.CubeMap;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.core.world.cube.BlankCube;
import io.github.opencubicchunks.cubicchunks.core.world.cube.Cube;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorDebug;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent.Load;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Modifies vanilla code in Chunk to use Cubes
 */
// TODO: redirect isChunkLoaded where needed
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mixin(value = Chunk.class, priority = 999)
public abstract class MixinChunk_Cubes implements IColumn {

    @Shadow @Final private ExtendedBlockStorage[] storageArrays;
    @Shadow @Final public static ExtendedBlockStorage NULL_BLOCK_STORAGE;

    @Shadow private boolean hasEntities;
    @Shadow @Final public int x;
    @Shadow @Final public int z;
    @Shadow @Final private ClassInheritanceMultiMap<Entity>[] entityLists;

    @Shadow @Final @Mutable private Map<BlockPos, TileEntity> tileEntities;

    @Shadow @Final private int[] heightMap;
    @Shadow @Final private World world;
    @Shadow private boolean loaded;
    @Shadow private boolean ticked;
    @Shadow private boolean isLightPopulated;
    @Shadow private boolean dirty;
    /*
     * WARNING: WHEN YOU RENAME ANY OF THESE 3 FIELDS RENAME CORRESPONDING
     * FIELDS IN "cubicchunks.asm.mixin.core.client.MixinChunk_Cubes" and
     * "cubicchunks.asm.mixin.core.common.MixinChunk_Columns".
     */
    private CubeMap cubeMap;
    private IHeightMap opacityIndex;
    private Cube cachedCube; // todo: make it always nonnull using BlankCube

    private boolean isColumn = false;

    @Shadow public abstract byte[] getBiomeArray();

    @Shadow public abstract int getHeightValue(int x, int z);

    public <T extends World & ICubicWorldInternal> T getWorld() {
        return (T) this.world;
    }

    // TODO: make it go through cube raw access methods
    // TODO: make cube an interface, use the implementation only here
    @Nullable
    private ExtendedBlockStorage getEBS_CubicChunks(int index) {
        if (!isColumn) {
            return storageArrays[index];
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            return cachedCube.getStorage();
        }
        Cube cube = getWorld().getCubeCache().getCube(this.x, index, this.z);
        if (!(cube instanceof BlankCube)) {
            cachedCube = cube;
        }
        return cube.getStorage();
    }

    @Nullable
    private ExtendedBlockStorage getLoadedEBS_CubicChunks(int index) {
        if (!isColumn) {
            return storageArrays[index];
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            return cachedCube.getStorage();
        }
        Cube cube = getWorld().getCubeCache().getLoadedCube(this.x, index, this.z);
        if (cube != null && !(cube instanceof BlankCube)) {
            cachedCube = cube;
        }
        return cube == null ? null : cube.getStorage();
    }

    // setEBS is unlikely to be used extremely frequently, no caching
    private void setEBS_CubicChunks(int index, ExtendedBlockStorage ebs) {
        if (!isColumn) {
            storageArrays[index] = ebs;
            return;
        }
        if (index >= 0 && index < 16) {
            storageArrays[index] = ebs;
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            cachedCube.setStorage(ebs);
            return;
        }
        Cube loaded = getWorld().getCubeCache().getLoadedCube(this.x, index, this.z);
        if (loaded.getStorage() == null) {
            loaded.setStorage(ebs);
        } else {
            throw new IllegalStateException(String.format(
                    "Attempted to set a Cube ExtendedBlockStorage that already exists. "
                            + "This is not supported. "
                            + "CubePos(%d, %d, %d), loadedCube(%s), loadedCubeStorage(%s)",
                    this.x, index, this.z,
                    loaded, loaded == null ? null : loaded.getStorage()));
        }
    }

    // modify vanilla:

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At(value = "RETURN"))
    private void cubicChunkColumn_construct(World world, int x, int z, CallbackInfo cbi) {
        if (!((ICubicWorld) world).isCubicWorld()) {
            return;
        }
        this.isColumn = true;
        // this.lightManager = world.getLightingManager();

        this.cubeMap = new CubeMap();
        //clientside we don't really need that much data. we actually only need top and bottom block Y positions
        if (world.isRemote) {
            this.opacityIndex = new ClientHeightMap((Chunk) (Object) this, heightMap);
        } else {
            this.opacityIndex = new ServerHeightMap(heightMap);
        }

        // instead of redirecting access to this map, just make the map do the work
        this.tileEntities = new ColumnTileEntityMap(this);

        // this.chunkSections = null;
        // this.skylightUpdateMap = null;

        Arrays.fill(getBiomeArray(), (byte) -1);
    }


    // private ExtendedBlockStorage getLastExtendedBlockStorage() - shouldn't be used by anyone

    // this method can't be saved by just redirecting EBS access
    @Inject(method = "getTopFilledSegment", at = @At(value = "HEAD"), cancellable = true)
    public void getTopFilledSegment_CubicChunks(CallbackInfoReturnable<Integer> cbi) {
        if (!isColumn) {
            return;
        }
        int blockY = Coords.NO_HEIGHT;
        for (int localX = 0; localX < Cube.SIZE; localX++) {
            for (int localZ = 0; localZ < Cube.SIZE; localZ++) {
                int y = this.opacityIndex.getTopBlockY(localX, localZ);
                if (y > blockY) {
                    blockY = y;
                }
            }
        }
        if (blockY < getWorld().getMinHeight()) {
            // PANIC!
            // this column doesn't have any blocks in it that aren't air!
            // but we can't return null here because vanilla code expects there to be a surface down there somewhere
            // we don't actually know where the surface is yet, because maybe it hasn't been generated
            // but we do know that the surface has to be at least at sea level,
            // so let's go with that for now and hope for the best

            int ret = Coords.cubeToMinBlock(Coords.blockToCube(this.getWorld().provider.getAverageGroundLevel()));
            cbi.setReturnValue(ret);
            cbi.cancel();
            return;
        }
        int ret = Coords.cubeToMinBlock(Coords.blockToCube(blockY)); // return the lowest block in the Cube (kinda weird I know)
        cbi.setReturnValue(ret);
        cbi.cancel();
    }

    /**
     * @author Barteks2x
     * @reason Original of that function return array of 16 objects
     **/
    @Overwrite
    public ExtendedBlockStorage[] getBlockStorageArray() {
       // if (isColumn) {
       //     // TODO: make the first 16 entries match vanilla
       //     return cubeMap.getStoragesToTick();
       // }
        return storageArrays;
    }

    /*
    Light update code called from this:

    if (addedNewCube) {
      generateSkylightMap();
    } else {
      if (placingOpaque) {
        if (placingNewTopBlock) {
          relightBlock(x, y + 1, z);
        } else if (removingTopBlock) {
          relightBlock(x, y, z);
        }
      }
      // equivalent to opacityDecreased || (opacityChanged && receivesLight)
      // which means: propagateSkylight if it lets more light through, or (it receives any light and opacity changed)
      if (opacityChanged && (opacityDecreased || blockReceivesLight)) {
        propagateSkylightOcclusion(x, z);
      }
    }
    */
    // ==============================================
    //             generateSkylightMap
    // ==============================================

    @Inject(method = "generateSkylightMap", at = @At(value = "HEAD"), cancellable = true)
    public void generateSkylightMap_CubicChunks_Replace(CallbackInfo cbi) {
        if (isColumn) {
            // TODO: update skylight in cubes marked for update
            cbi.cancel();
        }
    }

    // ==============================================
    //           propagateSkylightOcclusion
    // ==============================================

    @Inject(method = "propagateSkylightOcclusion", at = @At(value = "HEAD"), cancellable = true)
    private void propagateSkylightOcclusion_CubicChunks_Replace(int x, int z, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    //                 recheckGaps
    // ==============================================

    @Inject(method = "recheckGaps", at = @At(value = "HEAD"), cancellable = true)
    private void recheckGaps_CubicChunks_Replace(boolean p_150803_1_, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // private void checkSkylightNeighborHeight(int x, int z, int maxValue) - shouldn't be used by anyone

    // private void updateSkylightNeighborHeight(int x, int z, int startY, int endY) - shouldn't be used by anyone

    // ==============================================
    //                 relightBlock
    // ==============================================

    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;relightBlock(III)V"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void setBlockState_CubicChunks_relightBlockReplace(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir,
            int localX, int y, int localZ, int packedXZ, int oldHeightValue, IBlockState oldState, Block newBlock, Block oldBlock,
            int oldOpacity, ExtendedBlockStorage ebs, boolean createdNewEbsAboveTop, int newOpacity) {

        if (isColumn) {
            getWorld().getLightingManager().doOnBlockSetLightUpdates((Chunk) (Object) this, localX, oldHeightValue, y, localZ);
        }
    }
    
    // make relightBlock no-op for cubic chunks, handles by injection above
    @Inject(method = "relightBlock", at = @At(value = "HEAD"), cancellable = true)
    private void relightBlock_CubicChunks_Replace(int x, int y, int z, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    //            getBlockLightOpacity
    // ==============================================

    @Redirect(method = "getBlockLightOpacity(III)I", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;loaded:Z"))
    private boolean getBlockLightOpacity_isChunkLoadedCubeRedirect(Chunk chunk, int x, int y, int z) {
        if (!isColumn) {
            return loaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(y));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    //                 getBlockState
    // ==============================================

    // TODO: Use @ModifyConstant with expandConditions when it's implemented
    @Overwrite
    public IBlockState getBlockState(final int x, final int y, final int z) {
        if (this.getWorld().getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            IBlockState iblockstate = null;

            if (y == 60) {
                iblockstate = Blocks.BARRIER.getDefaultState();
            }

            if (y == 70) {
                iblockstate = ChunkGeneratorDebug.getBlockStateFor(x, z);
            }

            return iblockstate == null ? Blocks.AIR.getDefaultState() : iblockstate;
        } else {
            try {
                if (isColumn || (y >= 0 && y >> 4 < this.storageArrays.length)) {
                    ExtendedBlockStorage extendedblockstorage = getEBS_CubicChunks(blockToCube(y));

                    if (extendedblockstorage != NULL_BLOCK_STORAGE) {
                        return extendedblockstorage.get(blockToLocal(x), blockToLocal(y), blockToLocal(z));
                    }
                }

                return Blocks.AIR.getDefaultState();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Getting block state");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being got");
                crashreportcategory.addDetail("Location", () ->
                        CrashReportCategory.getCoordinateInfo(x, y, z)
                );
                throw new ReportedException(crashreport);
            }
        }
    }

    // ==============================================
    //                 setBlockState
    // ==============================================

    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;set"
            + "(IIILnet/minecraft/block/state/IBlockState;)V", shift = At.Shift.AFTER))
    private void onEBSSet_setBlockState_setOpacity(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        if (!isColumn) {
            return;
        }
        opacityIndex.onOpacityChange(blockToLocal(pos.getX()), pos.getY(), blockToLocal(pos.getZ()), state.getLightOpacity(world, pos));
        getWorld().getLightingManager().sendHeightMapUpdate(pos);
    }

    @Redirect(method = "setBlockState", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"
    ))
    private ExtendedBlockStorage setBlockState_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    @Redirect(method = "setBlockState", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=set"
    ))
    private void setBlockState_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index, ExtendedBlockStorage val) {
        setEBS_CubicChunks(index, val);
    }
    
    @Redirect(method = "setBlockState", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;dirty:Z"))
    private void setIsModifiedFromSetBlockState_Field(Chunk chunk, boolean isModifiedIn, BlockPos pos, IBlockState state) {
        if (isColumn) {
            getWorld().getCubeFromBlockCoords(pos).markDirty();
        } else {
            dirty = isModifiedIn;
        }
    }

    // ==============================================
    //                 getLightFor
    // ==============================================

    @Redirect(method = "getLightFor", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"
    ))
    private ExtendedBlockStorage getLightFor_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    //                 setLightFor
    // ==============================================

    @Redirect(method = "setLightFor", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"
    ))
    private ExtendedBlockStorage setLightFor_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    @Redirect(method = "setLightFor", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=set"
    ))
    private void setLightFor_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index, ExtendedBlockStorage ebs) {
        setEBS_CubicChunks(index, ebs);
    }
    
    @Redirect(method = "setLightFor", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;dirty:Z"))
    private void setIsModifiedFromSetLightFor_Field(Chunk chunk, boolean isModifiedIn, EnumSkyBlock type, BlockPos pos, int value) {
        if (isColumn) {
            getWorld().getCubeFromBlockCoords(pos).markDirty();
        } else {
            dirty = isModifiedIn;
        }
    }

    // ==============================================
    //             getLightSubtracted
    // ==============================================

    @Redirect(method = "getLightSubtracted", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"
    ))
    private ExtendedBlockStorage getLightSubtracted_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    //                  addEntity
    // ==============================================

    // TODO: Use @ModifyConstant with expandConditions when it's implemented
    @Overwrite
    public void addEntity(Entity entityIn) {
        this.hasEntities = true;
        int i = MathHelper.floor(entityIn.posX / 16.0D);
        int j = MathHelper.floor(entityIn.posZ / 16.0D);

        if (i != this.x || j != this.z) {
            CubicChunks.LOGGER.warn("Wrong location! ({}, {}) should be ({}, {}), {}", new Object[]{Integer.valueOf(i), Integer.valueOf(j), Integer
                    .valueOf(this.x), Integer.valueOf(this.z), entityIn});
            entityIn.setDead();
        }

        int k = MathHelper.floor(entityIn.posY / Cube.SIZE_D);

        if (k < Coords.blockToCube(getWorld().getMinHeight())) {
            k = Coords.blockToCube(getWorld().getMinHeight());
        }

        if (k >= Coords.blockToCube(getWorld().getMaxHeight())) {
            k = Coords.blockToCube(getWorld().getMaxHeight()) - 1;
        }

        MinecraftForge.EVENT_BUS
                .post(new net.minecraftforge.event.entity.EntityEvent.EnteringChunk(entityIn, this.x, this.z, entityIn.chunkCoordX,
                        entityIn.chunkCoordZ));
        entityIn.addedToChunk = true;
        entityIn.chunkCoordX = this.x;
        entityIn.chunkCoordY = k;
        entityIn.chunkCoordZ = this.z;
        
        if (!isColumn) {
            entityLists[k].add(entityIn);
        } else if (cachedCube != null && cachedCube.getY() == k) {
            cachedCube.getEntityContainer().addEntity(entityIn);
        } else {
            getWorld().getCubeCache().getCube(this.x, k, this.z).getEntityContainer().addEntity(entityIn);
        }
    }

    // ==============================================
    //             removeEntityAtIndex
    // ==============================================

    /**
     * @author Barteks2x
     * @reason original function limited to chunk entity list array field.
     */
    @Overwrite
    public void removeEntityAtIndex(Entity entityIn, int index) {
        if (index < Coords.blockToCube(getWorld().getMinHeight())) {
            index = Coords.blockToCube(getWorld().getMinHeight());
        }

        if (index >= Coords.blockToCube(getWorld().getMaxHeight())) {
            index = Coords.blockToCube(getWorld().getMaxHeight()) - 1;
        }

        if (!isColumn) {
            entityLists[index].remove(entityIn);
        } else if (cachedCube != null && cachedCube.getY() == index) {
            cachedCube.getEntityContainer().remove(entityIn);
        } else {
            getWorld().getCubeCache().getCube(this.x, index, this.z).getEntityContainer().remove(entityIn);
        }
    }

    // ==============================================
    //                addTileEntity
    // ==============================================

    @Redirect(method = "addTileEntity(Lnet/minecraft/tileentity/TileEntity;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;loaded:Z"))
    private boolean addTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, TileEntity te) {
        if (!isColumn) {
            return loaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(te.getPos().getY()));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    //              removeTileEntity
    // ==============================================

    @Redirect(method = "removeTileEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;loaded:Z"))
    private boolean removeTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, BlockPos pos) {
        if (!isColumn) {
            return loaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(pos.getY()));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    //                  onLoad
    // ==============================================

    @Inject(method = "onLoad", at = @At("HEAD"), cancellable = true)
    public void onChunkLoad_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();
        this.loaded = true;
        for (Cube cube : cubeMap) {
            cube.onLoad();
        }
        MinecraftForge.EVENT_BUS.post(new Load((Chunk) (Object) this));
    }

    // ==============================================
    //                onUnload
    // ==============================================

    @Inject(method = "onUnload", at = @At("HEAD"), cancellable = true)
    public void onChunkUnload_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();
        this.loaded = false;

        for (Cube cube : cubeMap) {
            cube.onUnload();
        }
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkEvent.Unload((Chunk) (Object) this));
    }

    // ==============================================
    //        getEntitiesWithinAABBForEntity
    // ==============================================

    @Inject(method = "getEntitiesWithinAABBForEntity", at = @At("HEAD"), cancellable = true)
    public void getEntitiesWithinAABBForEntity_CubicChunks(@Nullable Entity entityIn, AxisAlignedBB aabb,
            List<Entity> listToFill, Predicate<? super Entity> filter, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();

        int minY = MathHelper.floor((aabb.minY - World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        int maxY = MathHelper.floor((aabb.maxY + World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        minY = MathHelper.clamp(minY,
                blockToCube(getWorld().getMinHeight()),
                blockToCube(getWorld().getMaxHeight()));
        maxY = MathHelper.clamp(maxY,
                blockToCube(getWorld().getMinHeight()),
                blockToCube(getWorld().getMaxHeight()));

        for (Cube cube : cubeMap.cubes(minY, maxY)) {
            if (cube.getEntityContainer().getEntitySet().isEmpty()) {
                continue;
            }
            for (Entity entity : cube.getEntityContainer().getEntitySet()) {
                if (!entity.getEntityBoundingBox().intersects(aabb) || entity == entityIn) {
                    continue;
                }
                if (filter == null || filter.apply(entity)) {
                    listToFill.add(entity);
                }

                Entity[] parts = entity.getParts();

                if (parts != null) {
                    for (Entity part : parts) {
                        if (part != entityIn && part.getEntityBoundingBox().intersects(aabb)
                                && (filter == null || filter.apply(part))) {
                            listToFill.add(part);
                        }
                    }
                }
            }
        }
    }

    // ==============================================
    //          getEntitiesOfTypeWithinAABB
    // ==============================================

    @Inject(method = "getEntitiesOfTypeWithinAABB", at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void getEntitiesOfTypeWithinAAAB_CubicChunks(Class<? extends T> entityClass,
            AxisAlignedBB aabb, List<T> listToFill, Predicate<? super T> filter, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();

        int minY = MathHelper.floor((aabb.minY - World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        int maxY = MathHelper.floor((aabb.maxY + World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        minY = MathHelper.clamp(minY,
                blockToCube(getWorld().getMinHeight()),
                blockToCube(getWorld().getMaxHeight()));
        maxY = MathHelper.clamp(maxY,
                blockToCube(getWorld().getMinHeight()),
                blockToCube(getWorld().getMaxHeight()));

        for (Cube cube : cubeMap.cubes(minY, maxY)) {
            for (T t : cube.getEntityContainer().getEntitySet().getByClass(entityClass)) {
                if (t.getEntityBoundingBox().intersects(aabb) && (filter == null || filter.apply(t))) {
                    listToFill.add(t);
                }
            }
        }
    }

    // public boolean needsSaving(boolean p_76601_1_) - TODO: needsSaving

    // ==============================================
    //            getPrecipitationHeight
    // ==============================================

    @Inject(method = "getPrecipitationHeight", at = @At(value = "HEAD"), cancellable = true)
    private void getPrecipitationHeight_CubicChunks_Replace(BlockPos pos, CallbackInfoReturnable<BlockPos> cbi) {
        if (isColumn) {
            // TODO: precipitationHeightMap
            BlockPos ret = new BlockPos(pos.getX(), getHeightValue(blockToLocal(pos.getZ()), blockToLocal(pos.getZ())), pos.getZ());
            cbi.setReturnValue(ret);
            cbi.cancel();
        }
    }

    // ==============================================
    //                    onTick
    // ==============================================

    // TODO: check if we are out of time earlier?
    @Inject(method = "onTick", at = @At(value = "RETURN"))
    private void onTick_CubicChunks_TickCubes(boolean tryToTickFaster, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        this.ticked = true;
        this.isLightPopulated = true;
        // do nothing, we tick cubes directly
    }

    // ==============================================
    //               isEmptyBetween
    // ==============================================

    /**
     * @author Barteks2x
     * @reason original function limited to storage arrays.
     */
    @Overwrite
    public boolean isEmptyBetween(int startY, int endY) {
        if (startY < getWorld().getMinHeight()) {
            startY = getWorld().getMinHeight();
        }

        if (endY >= getWorld().getMaxHeight()) {
            endY = getWorld().getMaxHeight() - 1;
        }

        for (int i = startY; i <= endY; i += Cube.SIZE) {
            ExtendedBlockStorage extendedblockstorage = getEBS_CubicChunks(blockToCube(i));

            if (extendedblockstorage != NULL_BLOCK_STORAGE && !extendedblockstorage.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    // ==============================================
    //              setStorageArrays
    // ==============================================

    @Inject(method = "setStorageArrays", at = @At(value = "HEAD"))
    private void setStorageArrays_CubicChunks_NotSupported(ExtendedBlockStorage[] newStorageArrays, CallbackInfo cbi) {
        if (isColumn) {
            throw new UnsupportedOperationException("setting storage arrays it not supported with cubic chunks");
        }
    }

    // ==============================================
    //                  checkLight
    // ==============================================

    @Inject(method = "checkLight()V", at = @At(value = "HEAD"), cancellable = true)
    private void checkLight_CubicChunks_NotSupported(CallbackInfo cbi) {
        if (isColumn) {
            // todo: checkLight
            cbi.cancel();
        }
    }

    // private void setSkylightUpdated() - noone should use it

    // private void checkLightSide(EnumFacing facing) - noone should use it

    // private boolean checkLight(int x, int z) - TODO: checkLight

    @Overwrite
    public ClassInheritanceMultiMap<Entity>[] getEntityLists() {
        // TODO: return array of wrappers, 0-th entry will wrap everything below y=1, the 15-th entry will wrap everything above the top
        // at least if possible
        return this.entityLists;
    }

    // ==============================================
    //           removeInvalidTileEntity
    // ==============================================

    @Redirect(method = "removeInvalidTileEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;loaded:Z"))
    private boolean removeInvalidTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, BlockPos pos) {
        if (!isColumn) {
            return loaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(pos.getY()));
        return cube != null && cube.isCubeLoaded();
    }
}
