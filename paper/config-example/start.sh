# Arranque de un Paper 26.2 con 10 GB (Java 25), flags de Aikar tal cual las publica Paper.
# Xms = Xmx y 1-1,5 GB libres para el sistema: si el contenedor se queda sin RAM, el OOM killer lo tumba.
java -Xms10G -Xmx10G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar paper.jar --nogui

# Heaps grandes con muchos núcleos: ZGC, pausas de menos de 1 ms a cambio de algo de CPU y RAM.
# En Java 25 ya es generacional: -XX:+ZGenerational sobra (solo avisa) y en Java 26 impide arrancar.
# java -Xms16G -Xmx16G -XX:+UseZGC -XX:+AlwaysPreTouch -jar paper.jar --nogui

# Para medir antes de tocar nada: /spark profiler start --only-ticks-over 50 --timeout 600
