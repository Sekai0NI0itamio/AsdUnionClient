package com.mojang.blaze3d.systems;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BackendCreationException extends Exception {
    public BackendCreationException(final String message) {
        super(message);
    }
}
