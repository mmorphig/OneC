package pain.onec.mixin;

import meteordevelopment.meteorclient.gui.GuiThemes;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pain.onec.gui.screen.ServerScannerScreen;

@Mixin(MultiplayerScreen.class)
public abstract class OneCMultiplayerScreenMixin extends Screen {
    protected OneCMultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        // Server Scanner Button
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Server Scanner"), button -> {
                if (client != null) {
                    client.setScreen(new ServerScannerScreen(GuiThemes.get()));
                }
            })
            .position(this.width - 75 - 3 - 75 - 2 - 100 - 2, 3)
            .size(100, 20)
            .build()
        );
    }
}
