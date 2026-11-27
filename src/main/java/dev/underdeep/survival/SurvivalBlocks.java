package dev.underdeep.survival;

import dev.underdeep.registry.Reg;
import dev.underdeep.survival.SurvivalContent.Station;
import dev.underdeep.survival.home.BedrollBlock;
import dev.underdeep.survival.home.CompostBinBlock;
import dev.underdeep.survival.home.ContainerBlock;
import dev.underdeep.survival.home.ContainerBlockEntity;
import dev.underdeep.survival.home.DecorBlock;
import dev.underdeep.survival.home.RichSoilBlock;
import dev.underdeep.survival.home.SeatBlock;
import dev.underdeep.survival.home.SeatEntity;
import dev.underdeep.survival.station.DryingRackBlock;
import dev.underdeep.survival.station.DryingRackBlockEntity;
import dev.underdeep.survival.station.StationBlock;
import dev.underdeep.survival.station.StationBlockEntity;
import dev.underdeep.survival.station.StationScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/** Stage 7 blocks: workstations, homestead furniture, camping gear, soils. */
public final class SurvivalBlocks {
    private SurvivalBlocks() {
    }

    public static final TagKey<Block> HEAT_SOURCES = TagKey.of(RegistryKeys.BLOCK, Reg.id("heat_sources"));
    /** Blocks that count as furniture for home comfort. */
    public static final List<Block> FURNITURE = new ArrayList<>();
    public static final List<Block> STATIONS = new ArrayList<>();

    public static Block COOKING_POT, CLAY_OVEN, PREPARATION_TABLE, KETTLE, FERMENTATION_CROCK, HERBALIST_TABLE, DRYING_RACK, COMPOST_BIN,
            FISH_TRAP, FEEDING_TROUGH, RICH_SOIL, BEDROLL, CAMP_AWNING, WOODEN_CHAIR, WOODEN_BENCH, WOODEN_TABLE, STORAGE_CABINET, PANTRY,
            MEDICINE_CABINET, SUPPLY_CRATE, KITCHEN_COUNTER, PLANTER_BOX;
    public static BlockEntityType<StationBlockEntity> STATION_BE;
    public static BlockEntityType<DryingRackBlockEntity> RACK_BE;
    public static BlockEntityType<ContainerBlockEntity> CONTAINER_BE;
    public static ScreenHandlerType<StationScreenHandler> STATION_SCREEN;
    public static EntityType<SeatEntity> SEAT;

    static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
    }

    static AbstractBlock.Settings wood() {
        return AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(2f).sounds(BlockSoundGroup.WOOD).nonOpaque().burnable();
    }

    static AbstractBlock.Settings metal() {
        return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(2.5f).sounds(BlockSoundGroup.METAL).nonOpaque()
                .requiresTool();
    }

    static AbstractBlock.Settings stone() {
        return AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.5f).sounds(BlockSoundGroup.STONE).requiresTool();
    }

    private static <T extends Block> T station(String id, T b) {
        STATIONS.add(Reg.blockItem(id, b));
        return b;
    }

    private static <T extends Block> T furniture(String id, T b) {
        FURNITURE.add(Reg.blockItem(id, b));
        return b;
    }

    public static void init() {
        COOKING_POT = station("cooking_pot", new StationBlock(metal(), Station.POT, box(2, 0, 2, 14, 9, 14), false));
        CLAY_OVEN = station("clay_oven", new StationBlock(stone(), Station.OVEN, VoxelShapes.fullCube(), true));
        PREPARATION_TABLE = station("preparation_table", new StationBlock(wood(), Station.PREP, box(0, 0, 0, 16, 15, 16), true));
        KETTLE = station("kettle", new StationBlock(metal(), Station.KETTLE, box(3, 0, 3, 13, 9, 13), false));
        FERMENTATION_CROCK = station("fermentation_crock", new StationBlock(stone().nonOpaque(), Station.CROCK, box(4, 0, 4, 12, 14, 12), false));
        HERBALIST_TABLE = station("herbalist_table", new StationBlock(wood(), Station.HERBAL, box(0, 0, 0, 16, 15, 16), true));
        DRYING_RACK = station("drying_rack", Reg.cutout(new DryingRackBlock(wood().noCollision())));
        COMPOST_BIN = station("compost_bin", new CompostBinBlock(wood()));
        FISH_TRAP = station("fish_trap", Reg.cutout(new ContainerBlock(wood(), ContainerBlock.Kind.TRAP, box(2, 0, 2, 14, 10, 14))));
        FEEDING_TROUGH = station("feeding_trough", new ContainerBlock(wood(), ContainerBlock.Kind.TROUGH, box(1, 0, 1, 15, 6, 15)));
        RICH_SOIL = Reg.blockItem("rich_soil", new RichSoilBlock(AbstractBlock.Settings.create().mapColor(MapColor.DIRT_BROWN).strength(0.6f)
                .sounds(BlockSoundGroup.ROOTED_DIRT)));
        PLANTER_BOX = furniture("planter_box", new RichSoilBlock(wood().solid()));
        BEDROLL = furniture("bedroll", new BedrollBlock(AbstractBlock.Settings.create().mapColor(MapColor.RED).strength(0.3f)
                .sounds(BlockSoundGroup.WOOL).nonOpaque().burnable()));
        CAMP_AWNING = furniture("camp_awning", Reg.cutout(new DecorBlock(wood().noCollision(), box(0, 0, 0, 16, 14, 16), VoxelShapes.empty())));
        WOODEN_CHAIR = furniture("wooden_chair", new SeatBlock(wood(), box(2, 0, 2, 14, 9, 14), 0.35));
        WOODEN_BENCH = furniture("wooden_bench", new SeatBlock(wood(), box(0, 0, 3, 16, 8, 13), 0.3));
        WOODEN_TABLE = furniture("wooden_table", new DecorBlock(wood(), box(0, 0, 0, 16, 15, 16), box(0, 0, 0, 16, 15, 16)));
        STORAGE_CABINET = furniture("storage_cabinet", new ContainerBlock(wood(), ContainerBlock.Kind.CABINET, VoxelShapes.fullCube()));
        PANTRY = furniture("pantry", new ContainerBlock(wood(), ContainerBlock.Kind.PANTRY, VoxelShapes.fullCube()));
        MEDICINE_CABINET = furniture("medicine_cabinet", new ContainerBlock(wood(), ContainerBlock.Kind.MEDICINE, VoxelShapes.fullCube()));
        SUPPLY_CRATE = furniture("supply_crate", new ContainerBlock(wood(), ContainerBlock.Kind.CRATE, VoxelShapes.fullCube()));
        KITCHEN_COUNTER = furniture("kitchen_counter", new ContainerBlock(wood(), ContainerBlock.Kind.COUNTER, VoxelShapes.fullCube()));

        List<Block> stationBlocks = new ArrayList<>(List.of(COOKING_POT, CLAY_OVEN, PREPARATION_TABLE, KETTLE, FERMENTATION_CROCK, HERBALIST_TABLE));
        List<Block> equipmentStations = dev.underdeep.equipment.EquipmentItems.registerStationBlocks();
        stationBlocks.addAll(equipmentStations);
        STATIONS.addAll(equipmentStations);
        STATION_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Reg.id("station"), BlockEntityType.Builder.create(StationBlockEntity::new,
                stationBlocks.toArray(new Block[0])).build(null));
        RACK_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Reg.id("drying_rack"),
                BlockEntityType.Builder.create(DryingRackBlockEntity::new, DRYING_RACK).build(null));
        CONTAINER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Reg.id("container"), BlockEntityType.Builder.create(ContainerBlockEntity::new,
                FISH_TRAP, FEEDING_TROUGH, STORAGE_CABINET, PANTRY, MEDICINE_CABINET, SUPPLY_CRATE, KITCHEN_COUNTER).build(null));
        STATION_SCREEN = Registry.register(Registries.SCREEN_HANDLER, Reg.id("station"),
                new ExtendedScreenHandlerType<>(StationScreenHandler::new, StationScreenHandler.Data.CODEC));
        SEAT = Registry.register(Registries.ENTITY_TYPE, Reg.id("seat"), EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC)
                .dimensions(0.1f, 0.1f).disableSaving().disableSummon().makeFireImmune().maxTrackingRange(8).build("seat"));
    }
}
