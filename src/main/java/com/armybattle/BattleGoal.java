package com.armybattle;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/** Charge -> circle-strafe -> jump -> crit, like a real PvP player. */
public class BattleGoal extends Goal {
    private final SoldierEntity s;
    private LivingEntity target;
    private int pathTimer;
    private int strafeTimer;
    private int strafeDir = 1;

    public BattleGoal(SoldierEntity s) {
        this.s = s;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override public boolean canStart() { return s.shouldFight(); }
    @Override public boolean shouldContinue() { return s.shouldFight(); }

    @Override
    public void start() { target = null; pathTimer = 0; }

    @Override
    public void stop() {
        s.setSprinting(false);
        s.getNavigation().stop();
        target = null;
    }

    @Override
    public boolean shouldRunEveryTick() { return true; }

    @Override
    public void tick() {
        if (target == null || !target.isAlive() || s.age % 10 == 0) target = s.findEnemy();
        if (target == null) {
            s.setSprinting(false);
            s.getNavigation().stop();
            return;
        }

        double dist = s.distanceTo(target);
        s.getLookControl().lookAt(target, 40f, 40f);

        if (s.isUsingItem()) { // eating: stand and eat
            s.setSprinting(false);
            s.getNavigation().stop();
            return;
        }

        s.setSprinting(dist > 3.8);

        if (dist > 3.8) {
            if (--pathTimer <= 0) {
                pathTimer = 6 + s.getRandom().nextInt(6);
                s.getNavigation().startMovingTo(target, 1.0);
            }
        } else {
            if (--strafeTimer <= 0) {
                strafeTimer = 15 + s.getRandom().nextInt(25);
                strafeDir = s.getRandom().nextBoolean() ? 1 : -1;
            }
            if (--pathTimer <= 0) {
                pathTimer = 5;
                Vec3d away = s.getPos().subtract(target.getPos()).multiply(1, 0, 1);
                if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
                away = away.normalize();
                double a = Math.toRadians(35 * strafeDir);
                double cos = Math.cos(a), sin = Math.sin(a);
                Vec3d rot = new Vec3d(away.x * cos - away.z * sin, 0, away.x * sin + away.z * cos).multiply(2.4);
                Vec3d p = target.getPos().add(rot);
                s.getNavigation().startMovingTo(p.x, p.y, p.z, 1.0);
            }
        }

        // Attacks
        if (s.getAttackCooldown() <= 0 && dist <= 3.2) {
            boolean falling = !s.isOnGround() && s.getVelocity().y < -0.02 && s.fallDistance > 0;
            if (falling) {
                s.strike(target, true);                       // critical hit
            } else if (s.isOnGround() && s.getJumpCooldown() <= 0) {
                s.doJump();                                   // jump to set up the crit
                s.setJumpCooldown(14);
            } else if (s.isOnGround()) {
                s.strike(target, false);                      // landed early: normal hit
            }
        }
    }
}
