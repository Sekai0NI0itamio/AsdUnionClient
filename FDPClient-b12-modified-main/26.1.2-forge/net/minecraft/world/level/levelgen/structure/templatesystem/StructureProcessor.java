package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

public abstract class StructureProcessor {
    /** @deprecated Use variant with StructureTemplate argument */
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
        final LevelReader level,
        final BlockPos targetPosition,
        final BlockPos referencePos,
        final StructureTemplate.StructureBlockInfo originalBlockInfo,
        final StructureTemplate.StructureBlockInfo processedBlockInfo,
        final StructurePlaceSettings settings
    ) {
        return processedBlockInfo;
    }

    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
        final LevelReader level,
        final BlockPos targetPosition,
        final BlockPos referencePos,
        final StructureTemplate.StructureBlockInfo originalBlockInfo,
        final StructureTemplate.StructureBlockInfo processedBlockInfo,
        final StructurePlaceSettings settings,
        final @Nullable StructureTemplate template
    ) {
       return processBlock(level, targetPosition, referencePos, originalBlockInfo, processedBlockInfo, settings);
    }

    protected abstract StructureProcessorType<?> getType();

    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        final ServerLevelAccessor level,
        final BlockPos position,
        final BlockPos referencePos,
        final List<StructureTemplate.StructureBlockInfo> originalBlockInfoList,
        final List<StructureTemplate.StructureBlockInfo> processedBlockInfoList,
        final StructurePlaceSettings settings
    ) {
        return processedBlockInfoList;
    }

    /**
     * Use this method to process entities from a structure in much the same way as
     * blocks, parameters are analogous.
     */
    public StructureTemplate.@Nullable StructureEntityInfo processEntity(
        final LevelReader level,
        final BlockPos targetPosition,
        final StructureTemplate.StructureEntityInfo originalEntityInfo,
        final StructureTemplate.StructureEntityInfo processedEntityInfo,
        final StructurePlaceSettings settings,
        final @Nullable StructureTemplate template
    ) {
       return processedEntityInfo;
    }
}
