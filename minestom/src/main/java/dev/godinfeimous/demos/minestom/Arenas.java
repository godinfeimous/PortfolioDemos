package dev.godinfeimous.demos.minestom;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

/**
 * Cola de arenas: cada partida es una SharedInstance del mismo mapa (comparte sus chunks: abrir una no vuelve a cargar
 * el mundo) con su propio EventNode filtrado por instancia, así las reglas de una partida no tocan a las demás.
 * Con MIN jugadores arranca la cuenta atrás; al vaciarse, la arena se desregistra y libera su memoria.
 */
final class Arenas {

	private static final int MIN = 2, MAX = 8, COUNTDOWN = 10;

	private final InstanceContainer map;
	private final List<Arena> open = new ArrayList<>();
	private final AtomicInteger ids = new AtomicInteger();

	Arenas(InstanceContainer map) {
		this.map = map;
	}

	synchronized Arena join(Player player) {
		Arena arena = open.stream().filter(a -> !a.started && a.players < MAX).findFirst().orElseGet(this::create);
		arena.players++;
		return arena;
	}

	private Arena create() {
		Arena arena = new Arena(ids.incrementAndGet(), MinecraftServer.getInstanceManager().createSharedInstance(map));
		open.add(arena);
		MinecraftServer.getGlobalEventHandler().addChild(arena.events);
		return arena;
	}

	private synchronized void leave(Arena arena) {
		if (--arena.players > 0) return;
		open.remove(arena);
		if (arena.countdown != null) arena.countdown.cancel();
		MinecraftServer.getGlobalEventHandler().removeChild(arena.events);
		MinecraftServer.getInstanceManager().unregisterInstance(arena.instance);
	}

	final class Arena {
		private final SharedInstance instance;
		private final EventNode<InstanceEvent> events;
		private int players;
		private boolean started;
		private Task countdown;

		Arena(int id, SharedInstance instance) {
			this.instance = instance;
			this.events = EventNode.type("arena-" + id, EventFilter.INSTANCE, (event, target) -> target == instance);
			events.addListener(PlayerSpawnEvent.class, event -> {
				event.getPlayer().setGameMode(GameMode.ADVENTURE);
				if (players >= MIN && countdown == null) startCountdown();
			});
			events.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(!started));
			events.addListener(PlayerDisconnectEvent.class, event -> leave(this));
		}

		SharedInstance instance() {
			return instance;
		}

		private void startCountdown() {
			AtomicInteger left = new AtomicInteger(COUNTDOWN);
			countdown = MinecraftServer.getSchedulerManager().buildTask(() -> {
				int seconds = left.getAndDecrement();
				if (seconds == 0) {
					started = true;
					instance.getPlayers().forEach(p -> p.setGameMode(GameMode.SURVIVAL));
					instance.showTitle(Title.title(Component.text("¡A jugar!", NamedTextColor.GREEN), Component.empty()));
					countdown.cancel();
					return;
				}
				instance.showTitle(Title.title(Component.text(seconds, NamedTextColor.YELLOW), Component.text(players + " jugadores", NamedTextColor.GRAY),
						Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
			}).repeat(TaskSchedule.seconds(1)).schedule();
		}
	}
}
