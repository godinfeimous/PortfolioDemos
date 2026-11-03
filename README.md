# Portfolio Demos

Demos de código para la página de Minecraft del portfolio (godinfeimousportfolio.org/minecraft).
No es un plugin para producción: son piezas pequeñas que muestran cómo programo para servidores,
y **compilan contra las APIs reales** (con `-Xlint:all -Werror` en Paper, Velocity y Minestom).

| Módulo | Plataforma | Qué enseña |
|---|---|---|
| `core` | Java 25, sin Minecraft | Traspaso entre servidores con fencing tokens y su simulación determinista (`gradlew :core:simulate`), índice espacial de regiones medido con JMH (`gradlew :core:jmh`) y ejecutor con presupuesto por tick |
| `paper` | Paper 26.2 (`paper-api 26.2.build.135-stable`, Java 25) | Ver abajo |
| `velocity` | Velocity API 4.3.1 | Lobby de respaldo: si un servidor cae, el jugador va al lobby en vez de desconectarse |
| `minestom` | Minestom `2026.10.07-26.2` | Servidor de minijuegos desde cero: cola de arenas con `SharedInstance` y un `EventNode` por partida, detrás de Velocity |
| `fabric` | Fabric 1.21.11 (Loader 0.19.5, Loom 1.18.2, mapeos de Mojang) | Zoom suave con MixinExtras (`@ModifyReturnValue` en `getFov`) y menciones resaltadas con un Mixin en `ChatComponent.addMessage` |

### `paper`

| Clase | Qué enseña |
|---|---|
| `PlayerDataHandoff` | Datos del jugador entre servidores sin dupes: candado en Redis con fencing token (`SET NX PX` + `INCR` en un script Lua), claim y escrituras con `WHERE token <= ?` en MariaDB y el candado se suelta solo cuando MariaDB confirma |
| `MetricsExporter` | Exporter de Prometheus: MSPT p50/p95/máx., TPS, jugadores y entidades/chunks por mundo |
| `RegionContext` | Calculador de contextos de LuckPerms alimentado por regiones de WorldGuard (`region=spawn`), con caché por jugador |
| `ClaimOutline` | `/borde`: el borde de la protección con `BlockDisplay` que solo ve quien lo pide, con interpolación en el cliente |
| `DemoBootstrap` · `DemoLoader` | `paper-plugin.yml`: encantamiento propio con la API de registros y librerías resueltas de Maven al arrancar |
| `LagCompensatedReach` | Reach con compensación de lag: transacciones ping/pong con PacketEvents e historial de posiciones |
| `StatsRepository` | MariaDB asíncrona con HikariCP e hilos virtuales (UPSERT) |
| `VeinMiner` · `VeinFlag` | Pico de vena con PersistentDataContainer y flag propio de WorldGuard 7.0.20 |
| `RankDisplay` · `Promotions` | Prefijos y tracks con la API de LuckPerms 5.5 y comandos Brigadier |
| `ClickConsistency` | Chequeo de autoclicker por consistencia de clics con PacketEvents |
| `BedrockMenu` | Formulario nativo para Bedrock con la API de Geyser, cofre en Java |

`config-example/` (en la raíz, `velocity` y `paper`) trae la configuración de la red de ejemplo: docker-compose, Velocity,
Paper, LuckPerms en red y Geyser en el proxy. Son ejemplos sin secretos.

## Compilar

Necesita JDK 25 (Gradle lo busca con toolchains; el módulo de Fabric usa Java 21).

```
./gradlew build              # compila todo y pasa los tests (warnings como errores)
./gradlew :core:simulate     # tabla de la simulación del cambio de servidor
./gradlew :core:jmh          # benchmarks; resultados en core/build/jmh-results.json
```

Los extractos que salen en la web los genera `scripts/minecraft-demos.py` del portfolio a partir de estos archivos:
si cambias el código aquí, vuelve a ejecutarlo.

## Resultados medidos (Ryzen 7 5700X, Java 25)

`gradlew :core:simulate` (10.000 ejecuciones de 30 minutos por variante, 3 servidores, 2 % de escrituras colgadas 5–60 s):

| Variante | Ejecuciones con fallo | Rollbacks | Escrituras viejas rechazadas |
|---|---|---|---|
| Solo candado | 8.988 / 10.000 | 13.229 | 0 |
| Candado + fencing token | 0 / 10.000 | 0 | 21.631 |

`gradlew :core:jmh` (`RegionLookupBenchmark`, ns por consulta):

| Protecciones | Recorrer todas | Índice por chunk |
|---|---|---|
| 100 | 110 ± 31 | 14,3 ± 1,0 |
| 1.000 | 1.211 ± 166 | 14,5 ± 1,6 |
| 10.000 | 32.445 ± 1.420 | 25,6 ± 2,6 |
