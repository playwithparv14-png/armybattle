package com.armybattle;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ArmorTrim;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.item.trim.ArmorTrimPatterns;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

public class SoldierEntity extends PathAwareEntity {
    public static final int RED = 0;
    public static final int BLUE = 1;
    private static final int EQUIP_STAGES = 6; // boots, legs, chest, helmet, sword, totem

    private static final TrackedData<Integer> ARMY =
            DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private boolean charging;
    private int chargeDelay;
    private int equipStage;
    private int equipTimer = 20;
    private boolean instantGear;
    private int attackCooldown;
    private int jumpCooldown;
    private int eatCooldown;
    private int goldenApples;
    private int spareTotems;
    private int totemRefillTimer;
    private ItemStack stashedWeapon = ItemStack.EMPTY;

    public SoldierEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setEquipmentDropChance(slot, 0.0f);
        }
        this.goldenApples = 2 + this.random.nextInt(3);
        this.spareTotems = this.random.nextFloat() < 0.4f ? 1 : 0;
        this.equipTimer = 10 + this.random.nextInt(60); // each soldier starts gearing at a different time
    }

    public static DefaultAttributeContainer.Builder createSoldierAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 128.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new BattleGoal(this));
        this.goalSelector.add(5, new LookAroundGoal(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ARMY, RED);
    }

    // ---------- getters / setters ----------
    public int getArmy() { return this.dataTracker.get(ARMY); }
    public void setArmy(int army) { this.dataTracker.set(ARMY, army); }
    public void setInstantGear(boolean v) { this.instantGear = v; }
    public boolean isReady() { return equipStage >= EQUIP_STAGES; }
    public boolean shouldFight() { return charging && chargeDelay <= 0 && isReady(); }
    public void startCharge(int delay) { this.charging = true; this.chargeDelay = delay; }
    public void holdPosition() { this.charging = false; }
    public int getAttackCooldown() { return attackCooldown; }
    public int getJumpCooldown() { return jumpCooldown; }
    public void setJumpCooldown(int v) { this.jumpCooldown = v; }
    public void doJump() { this.jump(); }

    public void finishGearNow() {
        while (equipStage < EQUIP_STAGES) equipStep();
    }

    // ---------- tick ----------
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (attackCooldown > 0) attackCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (eatCooldown > 0) eatCooldown--;
        if (charging && chargeDelay > 0) chargeDelay--;
        tickEquipment();
        tickNeeds();
    }

    private void tickEquipment() {
        if (equipStage >= EQUIP_STAGES) return;
        if (instantGear) { finishGearNow(); return; }
        if (--equipTimer > 0) return;
        equipStep();
        equipTimer = 12 + this.random.nextInt(28);
    }

    private void equipStep() {
        switch (equipStage) {
            case 0 -> this.equipStack(EquipmentSlot.FEET, armor(Items.NETHERITE_BOOTS, true));
            case 1 -> this.equipStack(EquipmentSlot.LEGS, armor(Items.NETHERITE_LEGGINGS, false));
            case 2 -> this.equipStack(EquipmentSlot.CHEST, armor(Items.NETHERITE_CHESTPLATE, false));
            case 3 -> this.equipStack(EquipmentSlot.HEAD, armor(Items.NETHERITE_HELMET, false));
            case 4 -> {
                ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
                ench(sword, Enchantments.SHARPNESS, 5);
                ench(sword, Enchantments.UNBREAKING, 3);
                this.equipStack(EquipmentSlot.MAINHAND, sword);
            }
            case 5 -> this.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            default -> { return; }
        }
        equipStage++;
        this.swingHand(Hand.MAIN_HAND);
        if (equipStage <= 4) {
            this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.0f, 1.0f);
        } else {
            this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f);
        }
    }

    private ItemStack armor(Item item, boolean boots) {
        ItemStack s = new ItemStack(item);
        ench(s, Enchantments.PROTECTION, 4);
        ench(s, Enchantments.UNBREAKING, 3);
        if (boots) ench(s, Enchantments.FEATHER_FALLING, 4);
        s.set(DataComponentTypes.TRIM, trim());
        return s;
    }

    private ArmorTrim trim() {
        var reg = this.getWorld().getRegistryManager();
        var material = reg.getWrapperOrThrow(RegistryKeys.TRIM_MATERIAL)
                .getOrThrow(getArmy() == RED ? ArmorTrimMaterials.REDSTONE : ArmorTrimMaterials.LAPIS);
        var pattern = reg.getWrapperOrThrow(RegistryKeys.TRIM_PATTERN).getOrThrow(ArmorTrimPatterns.SENTRY);
        return new ArmorTrim(material, pattern);
    }

    private void ench(ItemStack stack, RegistryKey<Enchantment> key, int level) {
        RegistryEntry<Enchantment> e = this.getWorld().getRegistryManager()
                .getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key);
        stack.addEnchantment(e, level);
    }

    // ---------- eating / totems ----------
    private void tickNeeds() {
        if (!isReady()) return;

        // Pull out a spare totem after one pops
        if (getOffHandStack().isEmpty() && spareTotems > 0) {
            if (++totemRefillTimer > 25) {
                this.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f);
                spareTotems--;
                totemRefillTimer = 0;
            }
        }

        // Finished eating -> switch back to the sword
        if (!stashedWeapon.isEmpty() && !this.isUsingItem()) {
            this.equipStack(EquipmentSlot.MAINHAND, stashedWeapon);
            stashedWeapon = ItemStack.EMPTY;
            eatCooldown = 100;
        }

        // Low health -> eat a golden apple
        if (shouldFight() && stashedWeapon.isEmpty() && !this.isUsingItem()
                && eatCooldown <= 0 && goldenApples > 0
                && this.getHealth() <= this.getMaxHealth() * 0.5f) {
            stashedWeapon = this.getMainHandStack().copy();
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_APPLE));
            this.setCurrentHand(Hand.MAIN_HAND);
            goldenApples--;
        }
    }

    // ---------- combat ----------
    public LivingEntity findEnemy() {
        List<SoldierEntity> list = this.getWorld().getEntitiesByClass(SoldierEntity.class,
                this.getBoundingBox().expand(128.0),
                e -> e != this && e.isAlive() && e.getArmy() != this.getArmy());
        return list.stream().min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
    }

    public boolean strike(LivingEntity target, boolean crit) {
        this.swingHand(Hand.MAIN_HAND);
        EntityAttributeInstance inst = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        double base = inst.getBaseValue();
        if (crit) inst.setBaseValue(base + this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 0.5);
        boolean hit = this.tryAttack(target);
        inst.setBaseValue(base);

        if (hit && this.getWorld() instanceof ServerWorld sw) {
            if (crit) {
                sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(),
                        15, 0.3, 0.3, 0.3, 0.3);
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, this.getSoundCategory(), 1.0f, 1.0f);
            } else {
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, this.getSoundCategory(), 1.0f, 1.0f);
            }
        }
        attackCooldown = 12 + this.random.nextInt(5); // slightly imperfect timing like a human
        return hit;
    }

    // ---------- misc ----------
    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Army", getArmy());
        nbt.putInt("EquipStage", equipStage);
        nbt.putInt("Apples", goldenApples);
        nbt.putInt("SpareTotems", spareTotems);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setArmy(nbt.getInt("Army"));
        equipStage = nbt.getInt("EquipStage");
        goldenApples = nbt.getInt("Apples");
        spareTotems = nbt.getInt("SpareTotems");
    }
}
