package pain.onec.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.util.math.Box;

import pain.onec.OneC;

/* 
 * AutoArmorMend module
 * By mmorphig
 */

public class AutoArmorMend extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Throw bottle o' enchanting if armor durability falls below this percentage.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> mendToFull = sgGeneral.add(new BoolSetting.Builder()
        .name("mend-to-full")
        .description("Mend armor until fully repaired once under threshold.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> onlyWhenStationary = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-stationary")
        .description("Only throw when you aren't moving.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> requireBlockUnder = sgGeneral.add(new BoolSetting.Builder()
        .name("require-block")
        .description("Only throw if there's a block under you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switch back to the previous hotbar slot after throwing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Cooldown between bottle throws.")
        .min(0)
        .sliderRange(0, 100)
        .defaultValue(1)
        .build()
    );

    private int throwCooldown = 0;
    private boolean fullMendActive = false;

    public AutoArmorMend() {
        super(OneC.Main1c, "auto-armor-mend", "Automatically throws bottle o' enchanting to mend armor below durability threshold.");
    }

    @Override
    public void onActivate() {
        throwCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || (onlyWhenStationary.get() && !PlayerUtils.isMoving())) return;

        if (throwCooldown > 0) {
            throwCooldown--;
            return;
        }

        if (requireBlockUnder.get()) {
            BlockPos below = mc.player.getBlockPos().down();
            BlockState state = mc.world.getBlockState(below);
            boolean isSolid = state.isSolidBlock(mc.world, below);
            if (!isSolid) return;
        }

        if (shouldThrowBottle()) {
            int bottleSlot = findBottleInHotbar();
            if (bottleSlot != -1) {
                int previousSlot = mc.player.getInventory().selectedSlot;
                float originalPitch = mc.player.getPitch();

                mc.player.setPitch(90f);

                mc.player.getInventory().selectedSlot = bottleSlot;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

                mc.player.setPitch(originalPitch);
                if (switchBack.get()) mc.player.getInventory().selectedSlot = previousSlot;

                throwCooldown = maxCooldown.get();
            }
        }
    }

    private ItemStack getArmor(int slot) {
        // 0=boots, 1=leggings, 2=chestplate, 3=helmet
        return mc.player.getInventory().getStack(36 + slot);
    }


    private boolean shouldThrowBottle() {
        int nearbyXP = 0;
        Box area = new Box(
            mc.player.getX() - 1, mc.player.getY() - 1, mc.player.getZ() - 1,
            mc.player.getX() + 1, mc.player.getY() + 1, mc.player.getZ() + 1
        );

        for (ExperienceOrbEntity orb : mc.world.getEntitiesByClass(ExperienceOrbEntity.class, area, e -> true)) {
            nearbyXP += orb.getOrbSize();
        }

        int durabilityFromXP = nearbyXP * 2;

        int armorsWithMaxDur = 0;

        for (int slot = 0; slot < 4; slot++) {
            ItemStack armor = getArmor(slot);

            if (armor.isEmpty()) continue;
            if (!armor.hasEnchantments()) continue;
            if (!Utils.hasEnchantments(armor, Enchantments.MENDING)) continue;

            int maxDur = armor.getMaxDamage();
            int currentDur = maxDur - armor.getDamage();
            int adjustedDur = Math.min(currentDur + durabilityFromXP, maxDur);
            int durabilityPercent = adjustedDur * 100 / maxDur;

            if (currentDur == maxDur) armorsWithMaxDur++;

            if (fullMendActive) {
                if (durabilityPercent < 100) return true;
            } else if (durabilityPercent < durabilityThreshold.get()) {
                if (mendToFull.get()) fullMendActive = true;
                return true;
            }
        }

        if (armorsWithMaxDur == 4) fullMendActive = false;
        return false;
    }

    private int findBottleInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ExperienceBottleItem) return i;
        }
        return -1;
    }
}
