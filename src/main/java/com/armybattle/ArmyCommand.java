package com.armybattle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ArmyCommand {
    private ArmyCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("army").requires(s -> s.hasPermissionLevel(2))
                .then(literal("battle")
                        .then(argument("perSide", IntegerArgumentType.integer(1, 150))
                                .executes(c -> battle(c.getSource(), IntegerArgumentType.getInteger(c, "perSide"), 50, false))
                                .then(argument("distance", IntegerArgumentType.integer(10, 200))
                                        .executes(c -> battle(c.getSource(), IntegerArgumentType.getInteger(c, "perSide"),
                                                IntegerArgumentType.getInteger(c, "distance"), false))
                                        .then(literal("instant")
                                                .executes(c -> battle(c.getSource(), IntegerArgumentType.getInteger(c, "perSide"),
                                                        IntegerArgumentType.getInteger(c, "distance"), true))))))
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("hold").executes(c -> hold(c.getSource())))
                .then(literal("equipnow").executes(c -> equipNow(c.getSource())))
                .then(literal("clear").executes(c -> clear(c.getSource()))));
    }

    private static int battle(ServerCommandSource src, int count, int distance, boolean instant) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Run this as a player.")); return 0; }
        ServerWorld world = player.getServerWorld();

        double yaw = Math.toRadians(player.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));   // way you look (army width runs along this)
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));    // armies are placed along this
        Vec3d pos = player.getPos();

        // You stand between the armies; red on your left, blue on your right
        spawnArmy(world, player, SoldierEntity.RED, count, pos.subtract(right.multiply(distance / 2.0)), forward, right, instant);
        spawnArmy(world, player, SoldierEntity.BLUE, count, pos.add(right.multiply(distance / 2.0)), forward, right.negate(), instant);

        src.sendFeedback(() -> Text.literal("Spawned " + count + " red + " + count + " blue soldiers. "
                + (instant ? "Use /army start." : "They are gearing up; use /army start when ready.")), true);
        return 1;
    }

    private static void spawnArmy(ServerWorld world, ServerPlayerEntity player, int army, int count,
                                  Vec3d center, Vec3d lateral, Vec3d face, boolean instant) {
        Vec3d away = face.negate();
        int cols = Math.max(1, Math.min(count, (int) Math.ceil(Math.sqrt(count * 2.0))));
        float yaw = (float) (MathHelper.atan2(face.z, face.x) * 180.0 / Math.PI) - 90.0f;

        for (int i = 0; i < count; i++) {
            int row = i / cols, col = i % cols;
            double lat = (col - (cols - 1) / 2.0) * 1.9;
            double dep = row * 1.9;
            Vec3d p = center.add(lateral.multiply(lat)).add(away.multiply(dep));

            double y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(p.x), MathHelper.floor(p.z));
            if (Math.abs(y - player.getY()) > 12) y = player.getY();

            SoldierEntity e = ArmyBattleMod.SOLDIER.create(world);
            if (e == null) continue;
            e.setArmy(army);
            e.setInstantGear(instant);
            e.refreshPositionAndAngles(p.x, y, p.z, yaw, 0f);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);
            world.spawnEntity(e);
        }
    }

    private static int start(ServerCommandSource src) {
        int n = 0;
        for (ServerWorld w : src.getServer().getWorlds())
            for (SoldierEntity e : w.getEntitiesByType(ArmyBattleMod.SOLDIER, x -> true)) {
                e.startCharge(e.getRandom().nextInt(25)); // staggered start looks natural
                n++;
            }
        int total = n;
        src.sendFeedback(() -> Text.literal("CHARGE! (" + total + " soldiers; ones still gearing up will join when ready)"), true);
        return n;
    }

    private static int hold(ServerCommandSource src) {
        for (ServerWorld w : src.getServer().getWorlds())
            for (SoldierEntity e : w.getEntitiesByType(ArmyBattleMod.SOLDIER, x -> true)) e.holdPosition();
        src.sendFeedback(() -> Text.literal("Armies holding position."), true);
        return 1;
    }

    private static int equipNow(ServerCommandSource src) {
        for (ServerWorld w : src.getServer().getWorlds())
            for (SoldierEntity e : w.getEntitiesByType(ArmyBattleMod.SOLDIER, x -> true)) e.finishGearNow();
        src.sendFeedback(() -> Text.literal("Everyone fully geared."), true);
        return 1;
    }

    private static int clear(ServerCommandSource src) {
        int n = 0;
        for (ServerWorld w : src.getServer().getWorlds())
            for (SoldierEntity e : w.getEntitiesByType(ArmyBattleMod.SOLDIER, x -> true)) { e.discard(); n++; }
        int total = n;
        src.sendFeedback(() -> Text.literal("Removed " + total + " soldiers."), true);
        return n;
    }
}
