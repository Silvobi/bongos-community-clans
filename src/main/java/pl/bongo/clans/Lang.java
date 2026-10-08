package pl.bongo.clans;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Lang {
    private static final Map<String, Properties> LANGUAGES = Map.of("pl", load("pl"), "en", load("en"));
    private static Properties load(String name) {
        Properties properties = new Properties();
        try (var input = Lang.class.getResourceAsStream("/lang/" + name + ".properties")) {
            if (input == null) throw new IllegalStateException("Missing language: " + name);
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
        return properties;
    }
    public static String language(String clientLocale) { return clientLocale != null && clientLocale.toLowerCase(Locale.ROOT).startsWith("pl_") ? "pl" : "en"; }
    public static String text(String locale, String key, Object... args) {
        String pattern = LANGUAGES.get(language(locale)).getProperty(key);
        if (pattern == null) throw new IllegalArgumentException("Missing translation: " + key);
        return String.format(Locale.ROOT, pattern, args);
    }
    public static String text(ServerPlayer player, String key, Object... args) { return text(player.clientInformation().language(), key, args); }
    public static Component component(ServerPlayer player, String key, Object... args) { return Component.literal(text(player, key, args)); }
    public static Set<String> keys(String language) { return LANGUAGES.get(language).stringPropertyNames(); }
}
