package com.mojang.blaze3d.shaders;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum UniformType {
    UNIFORM_BUFFER("ubo"),
    TEXEL_BUFFER("utb");

    final String name;

    private UniformType(final String name) {
        this.name = name;
    }
}
