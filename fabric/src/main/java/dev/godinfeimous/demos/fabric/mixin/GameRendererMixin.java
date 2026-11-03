package dev.godinfeimous.demos.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.godinfeimous.demos.fabric.Zoom;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * MixinExtras en vez de @Overwrite o @Redirect: se modifica el valor que devuelve getFov sin reemplazar el método,
 * así conviven otros mods que también tocan el FOV (Sodium, OptiFine-likes, mods de cámara).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float godinfeimous$zoom(float fov) {
		return Zoom.apply(fov);
	}
}
