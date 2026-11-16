package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Hallazgo H-1 de la Fase 14: las consultas periódicas de la interfaz deben estar en
 * {@code tinyhat.security.session.poll-paths}; si no, cuentan como actividad y la sesión nunca
 * caduca por inactividad. La prueba lee el JavaScript real y la configuración real, de modo que
 * añadir un sondeo nuevo sin registrarlo hace fallar la compilación.
 */
class SessionPollPathsConsistencyTest {

	private static final Path JS = Path.of("src/main/resources/static/js/network-monitoring.js");
	private static final Path PROPERTIES = Path.of("src/main/resources/application.properties");
	private static final Pattern INTERVAL = Pattern.compile("window\\.setInterval\\(\\s*(\\w+)\\s*,");
	private static final Pattern API_PATH = Pattern.compile("\"(/api/[A-Za-z0-9/_-]+)");

	@Test
	void everyApiRoutePolledPeriodicallyByTheNetworkPageIsAPollPath() throws IOException {
		String script = Files.readString(JS, StandardCharsets.UTF_8);
		List<String> pollPaths = configuredPollPaths();
		AntPathMatcher matcher = new AntPathMatcher();

		Set<String> polled = new LinkedHashSet<>();
		Matcher intervals = INTERVAL.matcher(script);
		while (intervals.find()) {
			polled.addAll(apiPathsCalledBy(script, intervals.group(1)));
		}

		assertThat(polled)
			.as("la pantalla debe sondear al menos las rutas conocidas (si esto falla, cambió el JS: revisar la prueba)")
			.contains("/api/network/status", "/api/network/history", "/api/network/discoveries/history");
		assertThat(polled)
			.as("rutas sondeadas por setInterval que no están en poll-paths %s", pollPaths)
			.allSatisfy(path -> assertThat(pollPaths.stream().anyMatch(pattern -> matcher.match(pattern, path)))
				.as(path)
				.isTrue());
	}

	@Test
	void userDrivenActionsOfTheNetworkPageAreNotPollPaths() {
		// Las acciones del usuario deben seguir contando como actividad.
		AntPathMatcher matcher = new AntPathMatcher();
		List<String> pollPaths = configuredPollPathsUnchecked();
		for (String path : List.of("/api/network/scan", "/api/network/discover", "/api/network/devices",
			"/api/network/authorized-ranges", "/api/cves/buscar", "/api/organizaciones/x/invitaciones",
			"/proyectos", "/dashboard")) {
			assertThat(pollPaths.stream().anyMatch(pattern -> matcher.match(pattern, path)))
				.as("%s no debe ser un sondeo", path)
				.isFalse();
		}
	}

	private static List<String> apiPathsCalledBy(String script, String functionName) {
		Matcher start = Pattern.compile("(?:async\\s+)?function\\s+" + functionName + "\\s*\\(").matcher(script);
		if (!start.find()) {
			return List.of();
		}
		int open = script.indexOf('{', start.end());
		int depth = 0;
		int end = open;
		for (int i = open; i < script.length(); i++) {
			char c = script.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}' && --depth == 0) {
				end = i;
				break;
			}
		}
		List<String> paths = new ArrayList<>();
		Matcher api = API_PATH.matcher(script.substring(open, end));
		while (api.find()) {
			paths.add(api.group(1));
		}
		return paths;
	}

	private static List<String> configuredPollPaths() throws IOException {
		Properties properties = new Properties();
		properties.load(new StringReader(Files.readString(PROPERTIES, StandardCharsets.UTF_8)));
		List<String> paths = new ArrayList<>();
		for (int i = 0; properties.containsKey("tinyhat.security.session.poll-paths[" + i + "]"); i++) {
			paths.add(properties.getProperty("tinyhat.security.session.poll-paths[" + i + "]").trim());
		}
		assertThat(paths).isNotEmpty();
		return paths;
	}

	private static List<String> configuredPollPathsUnchecked() {
		try {
			return configuredPollPaths();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
