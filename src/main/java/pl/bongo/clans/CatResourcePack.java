package pl.bongo.clans;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional, embedded pack and a small local HTTP host for the test server. */
public final class CatResourcePack implements AutoCloseable {
    public final UUID id;
    public final String hash, url;
    private final byte[] bytes;
    private final String bindAddress;
    private final int port;
    private final boolean hostEnabled;
    private HttpServer host;
    private ExecutorService executor;

    public CatResourcePack(int minecraftPort) throws IOException {
        try (var input = getClass().getResourceAsStream("/cat/clapping-cat.zip")) {
            if (input == null) throw new IOException("Missing embedded cat resource pack");
            bytes = input.readAllBytes();
        }
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        id = UUID.nameUUIDFromBytes(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Path file = FabricLoader.getInstance().getConfigDir().resolve("bongo-community-clans-cat.properties");
        Properties config = new Properties();
        int defaultPort = minecraftPort >= 1 && minecraftPort < 65535 ? minecraftPort + 1 : 25566;
        config.setProperty("http.enabled", "true");
        config.setProperty("http.bind", "127.0.0.1");
        config.setProperty("http.port", Integer.toString(defaultPort));
        config.setProperty("pack.url", "http://127.0.0.1:" + defaultPort + "/clapping-cat.zip");
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) { config.load(input); }
        } else {
            Files.createDirectories(file.getParent());
            try (var output = Files.newOutputStream(file)) {
                config.store(output, "Cat test pack. Defaults support a client on this computer. For LAN/public clients set pack.url and http.bind, or use an external pack host with http.enabled=false.");
            }
        }
        bindAddress = config.getProperty("http.bind").trim();
        try { port = Integer.parseInt(config.getProperty("http.port")); }
        catch (NumberFormatException e) { throw new IOException("Invalid cat HTTP port", e); }
        if (port < 1 || port > 65535) throw new IOException("Cat HTTP port must be 1..65535");
        hostEnabled = Boolean.parseBoolean(config.getProperty("http.enabled"));
        url = config.getProperty("pack.url").trim();
        try {
            URI uri = URI.create(url);
            if (!(uri.getScheme().equals("http") || uri.getScheme().equals("https")) || uri.getHost() == null)
                throw new IllegalArgumentException("Use a direct HTTP(S) ZIP URL");
        } catch (RuntimeException e) { throw new IOException("Invalid cat pack.url", e); }
    }

    public synchronized void ensureHosted() throws IOException {
        if (!hostEnabled || host != null) return;
        HttpServer created = HttpServer.create(new InetSocketAddress(bindAddress, port), 16);
        ExecutorService workers = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().name("clans-cat-pack-", 0).factory());
        created.setExecutor(workers);
        created.createContext("/clapping-cat.zip", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals("/clapping-cat.zip")) {
                    exchange.sendResponseHeaders(404, -1); return;
                }
                boolean head = exchange.getRequestMethod().equals("HEAD");
                if (!head && !exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    exchange.sendResponseHeaders(405, -1); return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
                exchange.getResponseHeaders().set("ETag", "\"" + hash + "\"");
                exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
                if (!head) exchange.getResponseBody().write(bytes);
            }
        });
        created.start(); host = created; executor = workers;
        CommunityClans.LOGGER.info("Cat test pack available at {} (SHA-1 {})", url, hash);
    }

    @Override public synchronized void close() {
        if (host != null) { host.stop(0); host = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }
}
