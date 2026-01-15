package pain.onec.mixin;

import meteordevelopment.meteorclient.systems.config.Config;

import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.resource.SplashTextResourceSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Random;

@Mixin(SplashTextResourceSupplier.class)
public class OneCSplashTextMixin {
    @Unique
    private boolean override = true;
    @Unique
    private int currentIndex = 0;
    @Unique
    private final List<String> OneCSplashes = getOneCSplashes();

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onApply(CallbackInfoReturnable<SplashTextRenderer> cir) {
        if (Config.get() == null || !Config.get().titleScreenSplashes.get()) return;

        if (override) {
            currentIndex = new Random().nextInt(OneCSplashes.size());
            cir.setReturnValue(new SplashTextRenderer(Text.literal(OneCSplashes.get(currentIndex))));
        }
        override = !override;
    }

    @Unique
    private static List<String> getOneCSplashes() {
        return List.of(
                "Boop on the snoot!",
                "Blep!",
                "PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT PAT",
                "At room temperature",
                "Contains stairs",
                "This is a bucket",
                "It contains a bucket",
                "Mountains of... water?",
                "Masons from the start",
                "Nerds co.",
                "All columns",
                "No comment",
                "HER NAME IS KENZIE",
                "mamsnrhbr chehfde in der soder",
                "Unicorn Heads - A Mystical Experience"
        );
    }
}
