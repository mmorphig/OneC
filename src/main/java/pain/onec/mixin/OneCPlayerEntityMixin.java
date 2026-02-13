package pain.onec.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import pain.onec.modules.OneCFlight;


@Mixin(PlayerEntity.class)
public abstract class OneCPlayerEntityMixin extends LivingEntity {
    protected OneCPlayerEntityMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "getOffGroundSpeed", at = @At("HEAD"), cancellable = true)
    private void replaceGetOffGroundSpeed(CallbackInfoReturnable<Float> info) {
        if (!getEntityWorld().isClient()) return;

        if (Modules.get().get(OneCFlight.class).isActive()) {
            float speed = Modules.get().get(OneCFlight.class).getOffGroundSpeed();
            if (speed != -1) info.setReturnValue(speed);
        } else if (Modules.get().get(Flight.class).isActive()) {
			float speed = Modules.get().get(Flight.class).getOffGroundSpeed();
	        if (speed != -1) info.setReturnValue(speed);
		}
    }
}
