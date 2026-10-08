package pl.bongo.clans;

import static pl.bongo.clans.ClanData.*;
import java.util.*;
import java.util.function.LongSupplier;

public final class ClanService {
    public static final long INVITE_LIFETIME = 300_000;
    private final ClanStore store;
    private final LongSupplier clock;
    public ClanService(ClanStore store) { this(store, System::currentTimeMillis); }
    public ClanService(ClanStore store, LongSupplier clock) { this.store = store; this.clock = clock; }
    public State state() { return store.state(); }
    public Clan require(UUID player) { return require(state(), player); }
    private static Clan require(State s, UUID player) {
        Clan c = s.clan(player); if (c == null) throw new ClanException("no_clan"); return c;
    }
    private static void permission(Clan c, UUID actor, int power) {
        if (c.members.get(actor).role.power < power) throw new ClanException("no_permission");
    }
    public UUID create(UUID actor, String username, String rawName, String rawTag) {
        String name = ClanData.name(rawName), tag = ClanData.tag(rawTag);
        return store.update(s -> {
            if (s.clan(actor) != null) throw new ClanException("already_clan");
            unique(s, null, name, tag);
            Clan c = new Clan(); c.id = UUID.randomUUID(); c.name = name; c.tag = tag; c.createdAt = clock.getAsLong();
            c.members.put(actor, new Member(actor, username, Role.FOUNDER)); s.clans.put(c.id, c); s.invitations.remove(actor);
            s.user(actor).lastName = username; return c.id;
        });
    }
    public void invite(UUID actor, UUID target) {
        store.update(s -> {
            Clan c = require(s, actor); permission(c, actor, 1);
            if (s.clan(target) != null) throw new ClanException("target_has_clan");
            if (s.user(target).ignoreInvites) throw new ClanException("ignores_invites");
            Invitation old = s.invitations.get(target);
            if (old != null && old.expiresAt > clock.getAsLong()) throw new ClanException("invite_pending");
            s.invitations.put(target, new Invitation(c.id, actor, clock.getAsLong() + INVITE_LIFETIME)); return null;
        });
    }
    public void accept(UUID actor, String username) {
        store.update(s -> {
            if (s.clan(actor) != null) throw new ClanException("already_clan");
            Invitation i = s.invitations.get(actor);
            if (i == null || i.expiresAt <= clock.getAsLong()) throw new ClanException("no_invite");
            Clan c = s.clans.get(i.clan);
            if (c == null || !c.members.containsKey(i.inviter) || !c.members.get(i.inviter).role.manages()) throw new ClanException("no_invite");
            c.members.put(actor, new Member(actor, username, Role.MEMBER)); s.invitations.remove(actor);
            s.user(actor).lastName = username; return null;
        });
    }
    public void decline(UUID actor) { store.update(s -> { if (s.invitations.remove(actor) == null) throw new ClanException("no_invite"); return null; }); }
    public boolean ignore(UUID actor) { return store.update(s -> {
        User user = s.user(actor); user.ignoreInvites = !user.ignoreInvites;
        if (user.ignoreInvites) s.invitations.remove(actor); return user.ignoreInvites;
    }); }
    public boolean quickchat(UUID actor) { return store.update(s -> { require(s, actor); User u = s.user(actor); return u.quickchat = !u.quickchat; }); }
    public void kick(UUID actor, UUID target) {
        store.update(s -> {
            Clan c = require(s, actor); permission(c, actor, 1);
            Member m = c.members.get(target);
            if (m == null) throw new ClanException("not_member");
            if (actor.equals(target) || m.role.power >= c.members.get(actor).role.power) throw new ClanException("hierarchy");
            c.members.remove(target); s.user(target).quickchat = false; revoke(s, actor, target); return null;
        });
    }
    private static void revoke(State s, UUID actor, UUID removed) {
        s.invitations.values().removeIf(i -> i.inviter.equals(removed));
    }
    public void leave(UUID actor) {
        store.update(s -> {
            Clan c = require(s, actor);
            if (c.members.get(actor).role == Role.FOUNDER) throw new ClanException("founder_leave");
            c.members.remove(actor); s.user(actor).quickchat = false; revoke(s, actor, actor); return null;
        });
    }
    public void role(UUID actor, UUID target, Role role, boolean promote) {
        store.update(s -> {
            Clan c = require(s, actor); permission(c, actor, 2);
            Member a = c.members.get(actor), m = c.members.get(target);
            if (m == null) throw new ClanException("not_member");
            if (actor.equals(target) || m.role.power >= a.role.power || role.power >= a.role.power
                    || (promote ? role.power <= m.role.power : role.power >= m.role.power)) throw new ClanException("hierarchy");
            m.role = role;
            if (!role.manages()) revoke(s, actor, target); return null;
        });
    }
    public void transfer(UUID actor, UUID target) {
        store.update(s -> {
            Clan c = require(s, actor); permission(c, actor, 3);
            Member m = c.members.get(target);
            if (m == null || actor.equals(target)) throw new ClanException("not_member");
            c.members.get(actor).role = Role.COFOUNDER; m.role = Role.FOUNDER; return null;
        });
    }
    public void change(UUID actor, String field, String value) {
        store.update(s -> {
            Clan c = require(s, actor); permission(c, actor, 2);
            switch (field) {
                case "name" -> { String n = ClanData.name(value); unique(s, c.id, n, c.tag); c.name = n; }
                case "tag" -> { String t = ClanData.tag(value); unique(s, c.id, c.name, t); c.tag = t; }
                case "color" -> c.color = ClanData.color(value);
                default -> throw new IllegalArgumentException(field);
            }
            return null;
        });
    }
    public void delete(UUID actor, UUID expectedClan) {
        store.update(s -> {
            Clan c = require(s, actor); permission(c, actor, 3);
            if (!c.id.equals(expectedClan)) throw new ClanException("delete_expired");
            c.members.keySet().forEach(id -> s.user(id).quickchat = false);
            s.clans.remove(c.id); s.invitations.values().removeIf(i -> i.clan.equals(c.id)); return null;
        });
    }
    public UUID member(UUID actor, String name) {
        return require(actor).members.values().stream().filter(m -> m.name.equalsIgnoreCase(name)).map(m -> m.id).findFirst().orElseThrow(() -> new ClanException("not_member"));
    }
    public void remember(UUID actor, String name) {
        User u = state().users.get(actor); Clan c = state().clan(actor);
        if (u != null && name.equals(u.lastName) && (c == null || name.equals(c.members.get(actor).name))) return;
        store.update(s -> { s.user(actor).lastName = name; Clan clan = s.clan(actor); if (clan != null) clan.members.get(actor).name = name; return null; });
    }
}
