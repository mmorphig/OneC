package pain.onec.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.util.Hand;

import java.util.Map;
import java.util.HashMap;

import pain.onec.OneC;

import java.util.List;

/* 
 * AutoSplashPotion module
 * By mmorphig
 */

public class AutoSplashPotion extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<StatusEffect>> blockedEffects = sgGeneral.add(new StatusEffectListSetting.Builder()
        .name("blocked-effects")
        .description("Potion effects to ignore.")
        .defaultValue(
            net.minecraft.entity.effect.StatusEffects.SLOWNESS.value(),
            net.minecraft.entity.effect.StatusEffects.WEAKNESS.value(),
            net.minecraft.entity.effect.StatusEffects.POISON.value(),
            net.minecraft.entity.effect.StatusEffects.WIND_CHARGED.value(),
            net.minecraft.entity.effect.StatusEffects.OOZING.value(),
            net.minecraft.entity.effect.StatusEffects.INFESTED.value(),
            net.minecraft.entity.effect.StatusEffects.WEAVING.value(),
            net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.value()
        )
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

    public AutoSplashPotion() {
        super(OneC.Main1c, "auto-splash-potion", "Automatically throws splash potions downward.");
    }

    private int previousSlot = -1;
    private final Map<RegistryEntry<StatusEffect>, Integer> lastThrown = new HashMap<>();

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || (onlyWhenStationary.get() && !PlayerUtils.isMoving())) return;

        if (requireBlockUnder.get()) {
            BlockPos below = mc.player.getBlockPos().down();
            BlockState state = mc.world.getBlockState(below);
            boolean isSolid = state.isSolidBlock(mc.world, below);
            if (!isSolid) return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isValidSplashPotion(stack)) {
                float previousPitch = mc.player.getPitch();
                int previousSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = i;

                mc.player.setPitch(90f);

                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

                int currentTick = (int) mc.world.getTime();
                PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents == null) return;

                for (StatusEffectInstance potionEffect : contents.getEffects()) {
                    RegistryEntry<StatusEffect> effectEntry = potionEffect.getEffectType();
                    lastThrown.put(effectEntry, currentTick);
                }

                if (switchBack.get()) mc.player.getInventory().selectedSlot = previousSlot;
                mc.player.setPitch(previousPitch);

                break;
            }
        }
    }

    private boolean isValidSplashPotion(ItemStack stack) {
        if (!(stack.getItem() instanceof ThrowablePotionItem)) return false;

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;

        Iterable<StatusEffectInstance> effects = contents.getEffects();
        if (!effects.iterator().hasNext()) return false;

        int currentTick = (int) mc.world.getTime();

        for (StatusEffectInstance potionEffect : effects) {
            RegistryEntry<StatusEffect> effectEntry = potionEffect.getEffectType();
            StatusEffect effect = effectEntry.value();

            if (blockedEffects.get().contains(effect)) return false;

            if (lastThrown.containsKey(effectEntry)) {
                int last = lastThrown.get(effectEntry);
                if (currentTick - last < 20) return false;
            }

            if (mc.player.hasStatusEffect(effectEntry)) {
                StatusEffectInstance active = mc.player.getStatusEffect(effectEntry);
                if (active != null && active.getAmplifier() >= potionEffect.getAmplifier()) {
                    return false;
                }
            }
        }

        return true;
    }
}
