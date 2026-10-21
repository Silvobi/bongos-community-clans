package pl.bongo.clans.claims;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Intercept canonical GP handlers, so configured aliases and unified subcommands behave alike. */
public final class ClaimCommandHints {
    private ClaimCommandHints() {}
    public static boolean send(Object sender, String kind) {
        if (ClaimMenus.active == null) return false;
        Object player = GpClaims.call(sender, "requirePlayer");
        if (!(player instanceof ServerPlayer p)) return false;
        String pl = switch (kind) {
            case "add" -> "Aby dodać gracza do działki, użyj \"/manageclaim\".";
            case "remove" -> "Aby usunąć gracza z działki, użyj \"/manageclaim\".";
            case "flags" -> "Aby zmienić flagi działki, użyj \"/manageclaim\" lub \"/manageclaim global\".";
            case "allflags" -> "Aby zmienić domyślne flagi działek, użyj \"/manageclaim global\".";
            case "claims" -> "Aby zarządzać swoimi działkami, użyj \"/claims\".";
            default -> "Aby zarządzać dostępami do działki, użyj \"/manageclaim\".";
        };
        String en = switch (kind) {
            case "add" -> "To add a player to the claim, use \"/manageclaim\".";
            case "remove" -> "To remove a player from the claim, use \"/manageclaim\".";
            case "flags" -> "To change claim flags, use \"/manageclaim\" or \"/manageclaim global\".";
            case "allflags" -> "To change default claim flags, use \"/manageclaim global\".";
            case "claims" -> "To manage your claims, use \"/claims\".";
            default -> "To manage claim access, use \"/manageclaim\".";
        };
        p.sendSystemMessage(Component.literal(ClaimMenus.text(p, pl, en))); return true;
    }
}
