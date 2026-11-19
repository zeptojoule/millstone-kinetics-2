package com.jacaflavored.millstonekinetic.mixin;

import com.jacaflavored.millstonekinetic.KineticPower;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.baisylia.modestmining.block.custom.MillstoneBlock")
public abstract class MillstoneBlockMixin {

    /** Troca "tem redstone?" por "tem forca cinetica vinda de cima?" ao colocar o bloco e em updates de vizinhos. */
    @Redirect(
            method = {"getStateForPlacement", "neighborChanged"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean millstonekinetic$kineticInsteadOfRedstone(Level level, BlockPos pos) {
        return KineticPower.isPowered(level, pos);
    }

    /** A velocidade do Create muda sem avisar vizinhos, entao sincronizamos o estado a cada tick. */
    @Inject(method = "getTicker", at = @At("RETURN"), cancellable = true)
    private <T extends BlockEntity> void millstonekinetic$wrapTicker(Level level, BlockState state, BlockEntityType<T> type,
                                                                    CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        BlockEntityTicker<T> original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(KineticPower.wrapTicker(original));
        }
    }
}
