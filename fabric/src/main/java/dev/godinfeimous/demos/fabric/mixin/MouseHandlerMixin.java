package dev.godinfeimous.demos.fabric.mixin;

import dev.godinfeimous.demos.fabric.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Durante el zoom, la rueda ajusta el aumento y no llega a la barra rápida. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void godinfeimous$zoomScroll(long window, double horizontal, double vertical, CallbackInfo info) {
		if (!Zoom.active() || vertical == 0) return;
		Zoom.scroll(vertical);
		info.cancel();
	}
}
