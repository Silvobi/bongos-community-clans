package pl.bongo.clans;

import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Only color codes are interpreted; other ampersands remain literal tag text. */
public final class ClanTag {
    private ClanTag() {}
    private static final Pattern COLOR = Pattern.compile("&(?:#[0-9a-fA-F]{6}|[0-9a-fA-F])");
    private static final int[] LEGACY = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public static String plainText(String tag) { return COLOR.matcher(tag).replaceAll(""); }

    public static MutableComponent component(String tag, int defaultColor) {
        MutableComponent result = Component.empty();
        var matcher = COLOR.matcher(tag);
        int start = 0, color = defaultColor;
        while (matcher.find()) {
            if (matcher.start() > start) result.append(segment(tag.substring(start, matcher.start()), color));
            String code = matcher.group();
            color = code.charAt(1) == '#' ? Integer.parseInt(code.substring(2), 16)
                    : LEGACY[Character.digit(code.charAt(1), 16)];
            start = matcher.end();
        }
        if (start < tag.length()) result.append(segment(tag.substring(start), color));
        return result;
    }

    private static MutableComponent segment(String text, int color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }
}
