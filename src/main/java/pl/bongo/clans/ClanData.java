package pl.bongo.clans;

import java.text.Normalizer;
import java.util.*;

public final class ClanData {
    private ClanData() {}
    public enum Role {
        MEMBER(0), MANAGER(1), COFOUNDER(2), FOUNDER(3);
        public final int power;
        Role(int power) { this.power = power; }
        public boolean manages() { return power >= 1; }
        public boolean edits() { return power >= 2; }
        public static Role parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "member", "czlonek", "członek" -> MEMBER;
                case "manager", "zarzadca", "zarządca" -> MANAGER;
                case "cofounder", "wspolzalozyciel", "współzałożyciel" -> COFOUNDER;
                case "founder", "zalozyciel", "założyciel" -> FOUNDER;
                default -> throw new ClanException("bad_role");
            };
        }
    }
    public static class State {
        public int schemaVersion = 1;
        public Map<UUID, Clan> clans = new LinkedHashMap<>();
        public Map<UUID, User> users = new LinkedHashMap<>();
        public Map<UUID, Invitation> invitations = new LinkedHashMap<>();
        public Clan clan(UUID player) {
            return clans.values().stream().filter(c -> c.members.containsKey(player)).findFirst().orElse(null);
        }
        public User user(UUID player) { return users.computeIfAbsent(player, unused -> new User()); }
    }
    public static class Clan {
        public UUID id;
        public String name, tag;
        public int color = 0xFFFFFF; // Fallback for tags saved before inline colors were supported.
        public long createdAt;
        public Map<UUID, Member> members = new LinkedHashMap<>();
        public Member founder() { return members.values().stream().filter(m -> m.role == Role.FOUNDER).findFirst().orElseThrow(); }
    }
    public static class Member {
        public UUID id;
        public String name;
        public Role role;
        public Member(UUID id, String name, Role role) { this.id = id; this.name = name; this.role = role; }
    }
    public static class User {
        public boolean quickchat, ignoreInvites, backedUpTeam;
        public String previousTeam;
        public String lastName;
    }
    public static class Invitation {
        public UUID clan, inviter;
        public long expiresAt;
        public Invitation(UUID clan, UUID inviter, long expiresAt) { this.clan = clan; this.inviter = inviter; this.expiresAt = expiresAt; }
    }
    public static class ClanException extends RuntimeException {
        public final String key;
        public ClanException(String key) { super(key); this.key = key; }
    }
    public static String name(String raw) {
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        if (value.codePointCount(0, value.length()) > 32 || !value.matches("[\\p{L}\\p{Nd}_.-]+")) throw new ClanException("bad_name");
        return value;
    }
    public static String tag(String raw) {
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        String plain = ClanTag.plainText(value);
        if (plain.isBlank() || plain.codePointCount(0, plain.length()) > 12
                || value.codePoints().anyMatch(c -> c == 0xA7 || Character.isISOControl(c)
                || (c >= 0xD800 && c <= 0xDFFF) || c == 0x2028 || c == 0x2029)) throw new ClanException("bad_tag");
        return value;
    }
    public static void unique(State state, UUID except, String name, String tag) {
        for (Clan clan : state.clans.values()) if (!clan.id.equals(except)) {
            if (clan.name.equalsIgnoreCase(name)) throw new ClanException("name_taken");
            if (ClanTag.plainText(clan.tag).equalsIgnoreCase(ClanTag.plainText(tag))) throw new ClanException("tag_taken");
        }
    }
    public static void validate(State state) {
        if (state.schemaVersion != 1 || state.clans == null || state.users == null || state.invitations == null) throw new IllegalStateException("Unsupported clan data format");
        Set<UUID> members = new HashSet<>();
        for (var entry : state.clans.entrySet()) {
            Clan c = entry.getValue();
            if (!entry.getKey().equals(c.id) || c.members == null || c.members.isEmpty() || c.color < 0 || c.color > 0xFFFFFF
                    || c.members.values().stream().filter(m -> m.role == Role.FOUNDER).count() != 1) throw new IllegalStateException("Invalid clan structure");
            name(c.name); tag(c.tag); unique(state, c.id, c.name, c.tag);
            for (var member : c.members.entrySet()) {
                Member m = member.getValue();
                if (m.id == null || !member.getKey().equals(m.id) || m.name == null || m.role == null || !members.add(m.id)) throw new IllegalStateException("Invalid or duplicate member");
            }
        }
    }
}
