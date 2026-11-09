package com.armybattle;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ArmyBattleMod implements ModInitializer {
    public static final String MOD_ID = "armybattle";

    public static final EntityType<SoldierEntity> SOLDIER = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "soldier"),
            EntityType.Builder.create(SoldierEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .maxTrackingRange(10)
                    .build("soldier"));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(SOLDIER, SoldierEntity.createSoldierAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ArmyCommand.register(dispatcher));
    }
}
