package dev.godinfeimous.demos;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Las librerías (Redis, Prometheus, HikariCP) no van dentro del jar: Paper las resuelve de Maven Central al
 * arrancar y las pone en el classpath del plugin. El jar pesa unos KB y dos plugins no chocan por versiones.
 */
public final class DemoLoader implements PluginLoader {

	@Override
	public void classloader(PluginClasspathBuilder classpathBuilder) {
		MavenLibraryResolver resolver = new MavenLibraryResolver();
		resolver.addRepository(new RemoteRepository.Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
		for (String library : new String[] {
				"io.lettuce:lettuce-core:7.9.0.RELEASE",
				"io.prometheus:prometheus-metrics-core:1.10.0",
				"io.prometheus:prometheus-metrics-exporter-httpserver:1.10.0",
				"com.zaxxer:HikariCP:7.1.0"}) {
			resolver.addDependency(new Dependency(new DefaultArtifact(library), null));
		}
		classpathBuilder.addLibrary(resolver);
	}
}
