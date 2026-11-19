package com.jacaflavored.millstonekinetic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Decide se um Millstone está "ligado" olhando para o bloco ACIMA dele.
 * Usa reflexão para não precisar do Create em tempo de compilação.
 */
public final class KineticPower {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized = false;
    private static boolean failed = false;
    private static Class<?> iRotateClass;
    private static Class<?> kineticBeClass;
    private static Method hasShaftTowards;
    private static Method getSpeed;

    private KineticPower() {
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            iRotateClass = Class.forName("com.simibubi.create.content.kinetics.base.IRotate");
            kineticBeClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            hasShaftTowards = iRotateClass.getMethod("hasShaftTowards",
                    net.minecraft.world.level.LevelReader.class, BlockPos.class, BlockState.class, Direction.class);
            getSpeed = kineticBeClass.getMethod("getSpeed");
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("[MillstoneKinetic] Nao consegui acessar a API do Create; o Millstone nunca vai ligar!", t);
        }
    }

    /** RPM que equivale a velocidade normal (1x) de moagem. Hand crank = 32 RPM = 2x. */
    public static final float BASE_RPM = 16.0F;
    /** Limite de multiplicador de velocidade (ticks extras por tick). */
    public static final float MAX_MULTIPLIER = 16.0F;

    /** Velocidade (RPM, valor absoluto) do bloco acima se ele tem eixo apontando para o Millstone; 0 caso contrario. */
    public static float kineticSpeed(Level level, BlockPos pos) {
        init();
        if (failed) {
            return 0.0F;
        }
        try {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (!iRotateClass.isInstance(aboveState.getBlock())) {
                return 0.0F;
            }
            BlockEntity be = level.getBlockEntity(above);
            if (be == null || !kineticBeClass.isInstance(be)) {
                return 0.0F;
            }
            boolean shaftDown = (Boolean) hasShaftTowards.invoke(aboveState.getBlock(), level, above, aboveState, Direction.DOWN);
            if (!shaftDown) {
                return 0.0F;
            }
            return Math.abs((Float) getSpeed.invoke(be));
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("[MillstoneKinetic] Erro ao ler a velocidade cinetica; desativando a checagem.", t);
            return 0.0F;
        }
    }

    /** true se o bloco acima do Millstone esta girando e tem eixo apontando para ele. */
    public static boolean isPowered(Level level, BlockPos pos) {
        return kineticSpeed(level, pos) > 0.0F;
    }

    private static BlockState applyLit(Level level, BlockPos pos, BlockState state, boolean powered) {
        if (!state.hasProperty(BlockStateProperties.LIT)) {
            return state;
        }
        if (state.getValue(BlockStateProperties.LIT) != powered) {
            BlockState updated = state.setValue(BlockStateProperties.LIT, powered);
            level.setBlock(pos, updated, 3);
            return updated;
        }
        return state;
    }

    /**
     * Envolve o ticker original do Millstone: sincroniza o LIT com a forca cinetica e roda o tick
     * original varias vezes por tick conforme a velocidade (RPM / 16), acumulando fracoes.
     */
    public static <T extends BlockEntity> BlockEntityTicker<T> wrapTicker(BlockEntityTicker<T> original) {
        final float[] acc = new float[1];
        return (level, pos, state, be) -> {
            if (level.isClientSide) {
                original.tick(level, pos, state, be);
                return;
            }
            float speed = kineticSpeed(level, pos);
            BlockState current = applyLit(level, pos, state, speed > 0.0F);
            int times = 1;
            if (speed > 0.0F) {
                acc[0] += Math.min(speed / BASE_RPM, MAX_MULTIPLIER);
                times = (int) acc[0];
                acc[0] -= times;
            } else {
                acc[0] = 0.0F;
            }
            for (int i = 0; i < times; i++) {
                original.tick(level, pos, current, be);
            }
        };
    }
}
