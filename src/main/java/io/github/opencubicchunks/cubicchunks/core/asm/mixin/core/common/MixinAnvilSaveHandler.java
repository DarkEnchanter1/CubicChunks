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

import io.github.opencubicchunks.cubicchunks.core.CubicChunks;
import io.github.opencubicchunks.cubicchunks.core.server.chunkio.ICubeIO;
import io.github.opencubicchunks.cubicchunks.core.world.ICubicSaveHandler;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.chunk.storage.RegionFileCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

import javax.annotation.ParametersAreNonnullByDefault;

// a hook for flush()
// many mods already assume AnvilSaveHandler is always used, so we assume the same and hope for the best
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mixin(AnvilSaveHandler.class)
public class MixinAnvilSaveHandler implements ICubicSaveHandler {

    private ICubeIO cubeIo;

    @Inject(method = "flush", at = @At("RETURN"))
    public void onClearRefs(CallbackInfo cbi) throws IOException {
        if (cubeIo == null) {
            CubicChunks.bigWarning("cubeIo not initializes in save handler! If this happens frequently it's likely a major issue!");
            return;
        }
        cubeIo.flush();
    }

    @Override public void initCubic(ICubeIO cubeIo) {
        if (this.cubeIo != null) {
            CubicChunks.LOGGER.debug("Initializing cubeIo for cubic chunks save handler!");
            this.cubeIo = cubeIo;
        }
    }
}
