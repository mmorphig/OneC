package pain.onec.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Random;
import java.util.UUID;

import pain.onec.util.FakePlayerHandler;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;

import pain.onec.OneC;

/* Fun module :)
 * By mmorphig 
 */

public class Schizophrenia extends Module {
    private FakePlayerEntity hero;
    private int heroMode = 1;
    private int ticksRemaining = 0;
    private int ticksDelay = 0;
    private final Random rand = new Random();

    public Schizophrenia() {
        super(OneC.Main1c, "schizophrenia", "Reverts Mojang's attempts to \"Remove Herobrine\"");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        spawnHero();
    }

    @Override
    public void onDeactivate() {
        despawnHero();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (hero != null) {
            // Make the fake player look at the player
            Vec3d target = mc.player.getEyePos();
            Vec3d origin = hero.getEyePos();

            Vec3d diff = target.subtract(origin);
            double dx = diff.x;
            double dy = diff.y;
            double dz = diff.z;

            double distXZ = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

            hero.setYaw(yaw);
            hero.setBodyYaw(yaw);
            hero.setHeadYaw(yaw);
            hero.setPitch(pitch);

            // Check for proximity
            Vec3d playerPosVec = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d heroPosVec = new Vec3d(hero.getX(), hero.getY(), hero.getZ());
            double distance = playerPosVec.distanceTo(heroPosVec);

            Vec3d toHero = heroPosVec.subtract(mc.player.getEyePos()).normalize();
            Vec3d playerLook = mc.player.getRotationVec(1.0f).normalize();

            double dot = toHero.dotProduct(playerLook);
            double angleDegrees = Math.toDegrees(Math.acos(dot));

            if (heroMode == 1) {
                if (distance < 50 || angleDegrees < 2 || ticksRemaining-- <= 0 || distance > 200) {
                    despawnHero();
                }
            } else {
                if (angleDegrees < 20 || ticksRemaining-- <= 0 || distance > 100) {
                    despawnHero();
                }
            }
        } else {
            ticksDelay--;
            if (ticksDelay <= 0) {
                spawnHero();
            }
        }
    }


    private void spawnHero() {
        heroMode = rand.nextInt() % 2;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        double angle = rand.nextDouble() * 2 * Math.PI;
        double distance = 2;
        if (heroMode == 1) {
            distance = 75 + rand.nextDouble() * 75;
        } else {
            distance = 5 + rand.nextDouble() * 15;
        }

        double offsetX = MathHelper.cos((float) angle) * distance;
        double offsetZ = MathHelper.sin((float) angle) * distance;

        double spawnX = playerPos.x + offsetX;
        double spawnZ = playerPos.z + offsetZ;

        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) spawnX, (int) spawnZ);
        Vec3d spawnPos = new Vec3d(spawnX, topY, spawnZ);

        FakePlayerHandler.add("Herobrine", 20f, false);
        hero = FakePlayerHandler.get("Herobrine");
        if (hero != null) hero.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);

        ticksRemaining = 20 * (60 + rand.nextInt(241)); // 1–5 minutes
    }

    private void despawnHero() {
        if (hero != null) {
            FakePlayerHandler.remove(hero);
            hero = null;
            ticksDelay = 200;
        }
    }
}
