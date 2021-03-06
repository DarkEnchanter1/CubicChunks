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
package cubicchunks.world;

import static cubicchunks.lighting.LightingManager.MAX_CLIENT_LIGHT_SCAN_DEPTH;

import com.google.common.base.Throwables;
import cubicchunks.util.Coords;
import cubicchunks.world.column.IColumn;
import cubicchunks.world.cube.Cube;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ClientHeightMap implements IHeightMap {

    private final IColumn column;
    private final HeightMap hmap;
    private int heightMapLowest = Coords.NO_HEIGHT;

    public ClientHeightMap(IColumn column, int[] heightmap) {
        this.column = column;
        this.hmap = new HeightMap(heightmap);
    }

    @Override
    public boolean isOccluded(int localX, int blockY, int localZ) {
        int topY = this.getTopBlockY(localX, localZ);
        return blockY <= topY;
    }

    @Override
    public void onOpacityChange(int localX, int blockY, int localZ, int opacity) {
        writeNewTopBlockY(localX, blockY, localZ, opacity, getTopBlockY(localX, localZ));
    }

    private void writeNewTopBlockY(int localX, int changeY, int localZ, int newOpacity, int oldTopY) {
        //to avoid unnecessary delay when breaking blocks client needs to figure out new height before
        //server tells the client what it is
        //common cases first
        if (addedTopBlock(changeY, newOpacity, oldTopY)) {
            //added new block, so it's correct. Server update will be ignored
            this.setHeight(localX, localZ, changeY);
            return;
        }
        if (!changedTopToTransparent(changeY, newOpacity, oldTopY)) {
            //if not breaking the top block - no changes
            return;
        }
        assert !(newOpacity == 0 && oldTopY < changeY) : "Changed transparent block into transparent!";

        //changed the top block
        int newTop = oldTopY - 1;
        while (column.getBlockLightOpacity(new BlockPos(localX, newTop, localZ)) == 0 && newTop > oldTopY - MAX_CLIENT_LIGHT_SCAN_DEPTH) {
            newTop--;
        }
        //update the heightmap. If this update it not accurate - it will be corrected when server sends block update
        this.setHeight(localX, localZ, newTop);
    }

    private boolean changedTopToTransparent(int changeY, int newOpacity, int oldTopY) {
        return newOpacity == 0 && changeY == oldTopY;
    }

    private boolean addedTopBlock(int changeY, int newOpacity, int oldTopY) {
        return (changeY > oldTopY) && newOpacity != 0;
    }

    @Override
    public int getTopBlockY(int localX, int localZ) {
        return hmap.get(getIndex(localX, localZ));
    }

    @Override
    public int getLowestTopBlockY() {
        if (heightMapLowest == Coords.NO_HEIGHT) {
            heightMapLowest = Integer.MAX_VALUE;
            for (int i = 0; i < Cube.SIZE * Cube.SIZE; i++) {
                if (hmap.get(i) < heightMapLowest) {
                    heightMapLowest = hmap.get(i);
                }
            }
        }
        return heightMapLowest;
    }

    @Override
    public int getTopBlockYBelow(int localX, int localZ, int blockY) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void setHeight(int localX, int localZ, int height) {
        hmap.set(getIndex(localX, localZ), height);
    }

    public void setData(@Nonnull byte[] data) {
        try {
            ByteArrayInputStream buf = new ByteArrayInputStream(data);
            DataInputStream in = new DataInputStream(buf);

            for (int i = 0; i < Cube.SIZE * Cube.SIZE; i++) {
                hmap.set(i, in.readInt());
            }

            in.close();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static int getIndex(int localX, int localZ) {
        return (localZ << 4) | localX;
    }
}
