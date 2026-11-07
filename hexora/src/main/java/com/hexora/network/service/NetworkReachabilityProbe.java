package com.hexora.network.service;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetworkReachabilityProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkReachabilityProbe.class);
    private static final String ECHO_REPLY_PATTERN =
        "(?im)^\\s*(?:reply from|respuesta desde)\\s+%s:\\s*bytes=";

    boolean isReachable(InetAddress address, int timeoutMillis) {
        try {
            if (address.isReachable(timeoutMillis)) {
                return true;
            }
        } catch (IOException exception) {
            LOGGER.debug("Java ICMP reachability check failed for {}", address.getHostAddress(), exception);
        }
        if (isWindows()) {
            return pingWindows(address, timeoutMillis);
        }
        return isLinux() && pingLinux(address, timeoutMillis);
    }

    boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    boolean pingLinux(InetAddress address, int timeoutMillis) {
        int timeoutSeconds = Math.max(1, (timeoutMillis + 999) / 1_000);
        Process process;
        try {
            process = new ProcessBuilder(
                "ping",
                "-n",
                "-c", "1",
                "-W", Integer.toString(timeoutSeconds),
                address.getHostAddress()
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        } catch (IOException exception) {
            LOGGER.debug("Linux ping could not start for {}", address.getHostAddress(), exception);
            return false;
        }

        try {
            if (!process.waitFor(timeoutMillis + 1_000L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            LOGGER.debug("Linux ping was interrupted for {}", address.getHostAddress(), exception);
            return false;
        }
    }

    boolean pingWindows(InetAddress address, int timeoutMillis) {
        Process process;
        try {
            process = new ProcessBuilder(
                "ping.exe",
                "-n", "2",
                "-w", Integer.toString(timeoutMillis),
                address.getHostAddress()
            )
                .redirectErrorStream(true)
                .start();
        } catch (IOException exception) {
            LOGGER.debug("Windows ping could not start for {}", address.getHostAddress(), exception);
            return false;
        }

        try {
            if (!process.waitFor(timeoutMillis * 2L + 1_000L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
            return process.exitValue() == 0 && containsEchoReply(output, address.getHostAddress());
        } catch (IOException exception) {
            LOGGER.debug("Windows ping output could not be read for {}", address.getHostAddress(), exception);
            return false;
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            LOGGER.debug("Windows ping was interrupted for {}", address.getHostAddress(), exception);
            return false;
        }
    }

    static boolean containsEchoReply(String output, String address) {
        String expression = ECHO_REPLY_PATTERN.formatted(Pattern.quote(address));
        return Pattern.compile(expression).matcher(output).find();
    }
}
