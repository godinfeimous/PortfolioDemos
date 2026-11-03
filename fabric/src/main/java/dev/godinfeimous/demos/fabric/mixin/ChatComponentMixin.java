package dev.godinfeimous.demos.fabric.mixin;

import dev.godinfeimous.demos.fabric.Mentions;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Engancha las menciones al chat: cambia el mensaje justo al entrar en addMessage, antes de que se
 * parta en líneas. Así la marca también aparece al hacer scroll o al reabrir el chat.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component godinfeimous$highlightMention(Component message) {
		return Mentions.highlight(message);
	}
}
