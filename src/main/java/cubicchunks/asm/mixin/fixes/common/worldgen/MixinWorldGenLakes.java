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
package cubicchunks.asm.mixin.fixes.common.worldgen;

import static cubicchunks.util.Coords.blockToCube;
import static cubicchunks.util.Coords.cubeToMinBlock;

import cubicchunks.world.ICubicWorld;
import cubicchunks.worldgen.generator.custom.populator.PopulatorUtils;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenLakes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mixin(WorldGenLakes.class)
public class MixinWorldGenLakes {

    private int minY;

    @Inject(method = "generate", at = @At("HEAD"))
    private void onGenerate(World worldIn, Random rand, BlockPos position, CallbackInfoReturnable<Boolean> cbi) {
        this.minY = PopulatorUtils.getMinCubePopulationPos(position.getY());
    }

    @ModifyConstant(method = "generate", constant = @Constant(intValue = 5, ordinal = 0))
    private int getMinGenHeight(int orig, World worldIn, Random rand, BlockPos position) {
        if (((ICubicWorld) worldIn).isCubicWorld()) {
            return minY;
        }
        return orig;
    }

    @ModifyConstant(method = "generate", constant = @Constant(intValue = 4, ordinal = 0))
    private int getMinWorldHeight(int orig, World worldIn, Random rand, BlockPos position) {
        return ((ICubicWorld) worldIn).getMinHeight() + orig;
    }
}
