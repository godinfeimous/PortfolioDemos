package dev.godinfeimous.demos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.track.Track;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * `/promover <jugador>`: sube un escalón en el track "staff" (helper → mod → admin) con la API de LuckPerms.
 * modifyUser carga al usuario, aplica el cambio y lo guarda en segundo plano: el tick nunca espera a la base de datos.
 */
final class Promotions {

	private Promotions() {}

	static LiteralCommandNode<CommandSourceStack> command(LuckPerms luckPerms) {
		return Commands.literal("promover")
				.requires(source -> source.getSender().hasPermission("demo.promover"))
				.then(Commands.argument("jugador", ArgumentTypes.player())
						.executes(context -> {
							CommandSender sender = context.getSource().getSender();
							Player target = context.getArgument("jugador", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
							Track track = luckPerms.getTrackManager().getTrack("staff");
							if (track == null) {
								sender.sendRichMessage("<red>No existe el track <white>staff</white>.");
								return 0;
							}
							luckPerms.getUserManager().modifyUser(target.getUniqueId(), user -> {
								PromotionResult result = track.promote(user, ImmutableContextSet.empty());
								sender.sendRichMessage(result.wasSuccessful()
										? "<green>" + target.getName() + " ahora es <white>" + result.getGroupTo().orElse("?") + "</white>."
										: "<yellow>No se pudo promover: " + result.getStatus() + ".");
							});
							return Command.SINGLE_SUCCESS;
						}))
				.build();
	}
}
