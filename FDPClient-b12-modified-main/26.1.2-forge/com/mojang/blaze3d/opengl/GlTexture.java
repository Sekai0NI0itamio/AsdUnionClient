package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class GlTexture extends GpuTexture {
    private static final int EMPTY = -1;
    protected final int id;
    private int firstFboId = -1;
    private int firstFboDepthId = -1;
    private @Nullable Int2IntMap fboCache;
    protected boolean closed;
    private int views;

    protected GlTexture(
        @GpuTexture.Usage final int usage,
        final String label,
        final TextureFormat format,
        final int width,
        final int height,
        final int depthOrLayers,
        final int mipLevels,
        final int id
    ) {
        this(usage, label, format, width, height, depthOrLayers, mipLevels, id, false);
    }

    protected GlTexture(int usage, String label, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels, int id, boolean stencil) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
        this.id = id;
        this.stencilEnabled = stencil;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            if (this.views == 0) {
                this.destroyImmediately();
            }
        }
    }

    private void destroyImmediately() {
        GlStateManager._deleteTexture(this.id);
        if (this.firstFboId != -1) {
            GlStateManager._glDeleteFramebuffers(this.firstFboId);
        }

        if (this.fboCache != null) {
            for (int fbo : this.fboCache.values()) {
                GlStateManager._glDeleteFramebuffers(fbo);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    public int getFbo(final DirectStateAccess dsa, final @Nullable GpuTexture depth) {
        int depthId = depth == null ? 0 : ((GlTexture)depth).id;
        if (this.firstFboDepthId == depthId) {
            return this.firstFboId;
        } else if (this.firstFboId == -1) {
            this.firstFboId = this.createFbo(dsa, depthId);
            this.firstFboDepthId = depthId;
            return this.firstFboId;
        } else {
            if (this.fboCache == null) {
                this.fboCache = new Int2IntArrayMap();
            }

            return this.fboCache.computeIfAbsent(depthId, _depthId -> this.createFbo(dsa, _depthId));
        }
    }

    private int createFbo(final DirectStateAccess dsa, final int depthid) {
        int fbo = dsa.createFrameBufferObject();
        dsa.bindFrameBufferTextures(fbo, this.id, depthid, 0, 0);
        return fbo;
    }

    public int glId() {
        return this.id;
    }

    public void addViews() {
        this.views++;
    }

    public void removeViews() {
        this.views--;
        if (this.closed && this.views == 0) {
            this.destroyImmediately();
        }
    }

    private final boolean stencilEnabled;

    @Override
    public boolean isStencilEnabled() {
        return this.stencilEnabled;
    }
}
