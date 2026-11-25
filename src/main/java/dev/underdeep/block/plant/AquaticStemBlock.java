package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;

import java.util.function.Supplier;

/** Kelp-family underwater stalks (tip + body pair), e.g. ribbonweed and glowkelp. */
public final class AquaticStemBlock {
    private AquaticStemBlock() {
    }

    public static class Tip extends KelpBlock {
        private final Supplier<Block> body;

        public Tip(Supplier<Block> body, Settings settings) {
            super(settings);
            this.body = body;
        }

        @Override
        public MapCodec<KelpBlock> getCodec() {
            return MapCodec.unit(this);
        }

        @Override
        protected Block getPlant() {
            return body.get();
        }
    }

    public static class Body extends KelpPlantBlock {
        private final Supplier<Block> tip;

        public Body(Supplier<Block> tip, Settings settings) {
            super(settings);
            this.tip = tip;
        }

        @Override
        public MapCodec<KelpPlantBlock> getCodec() {
            return MapCodec.unit(this);
        }

        @Override
        protected AbstractPlantStemBlock getStem() {
            return (AbstractPlantStemBlock) tip.get();
        }
    }
}
