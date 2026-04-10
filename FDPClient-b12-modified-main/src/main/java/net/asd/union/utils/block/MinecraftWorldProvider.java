/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.utils.block;

// Pathfinding functionality removed
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

public class MinecraftWorldProvider {

    private final World world;

    public MinecraftWorldProvider(World world) {
        this.world = world;
    }

    public boolean isBlocked(int x, int y, int z) {
        return isSolid(x, y, z) || isSolid(x, y + 1, z) || unableToStand(x, y - 1, z);
    }

    private boolean isSolid(int x, int y, int z) {
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        if(block == null) return true;

        return block.getMaterial().isSolid();
    }

    private boolean unableToStand(int x, int y, int z) {
        final Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block instanceof BlockFence || block instanceof BlockWall;
    }
}