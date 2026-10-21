package pl.bongo.clans.claims;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import static pl.bongo.clans.claims.GpClaims.*;

/** GP3D 19 Fabric adapter. Mutations use its persistence and lifecycle callbacks. */
public final class ClaimAccess {
    final GpClaims gp;
    ClaimAccess(GpClaims gp) { this.gp = gp; }
    static final String PACKAGE = "com.griefprevention.fabric.mc26_1.";
    static Object invokeStatic(String type, String method, Object... args) {
        try {
            for (Method m : Class.forName(PACKAGE + type).getDeclaredMethods()) {
                if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
                m.setAccessible(true); return m.invoke(null, args);
            }
            throw new NoSuchMethodException(method);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("GP3D: " + method, e); }
    }
    Object level(String name) {
        try {
            for (Object e : Class.forName("com.griefprevention.claims.ClaimTrustLevel").getEnumConstants())
                if (((Enum<?>)e).name().equals(name)) return e;
            throw new IllegalArgumentException(name);
        } catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    long id(Object claim) { return (Long)call(claim, "id"); }
    Long parent(Object claim) { return (Long)call(claim, "parentId"); }
    Object root(Object claim) {
        while (parent(claim) != null) claim = Objects.requireNonNull(gp.byId(parent(claim)));
        return claim;
    }
    Object at(ServerPlayer player) { return call(gp.repository, "findClaimAt", player.level(), player.blockPosition()); }
    boolean allows(Object claim, ServerPlayer player, String level) { return (boolean)call(gp.repository, "allows", claim, player, level(level)); }
    boolean permission(ServerPlayer player, String permission) { return (boolean)call(gp.repository, "hasPermission", player, "griefprevention." + permission); }
    boolean own(Object claim, ServerPlayer player) {
        return player.getUUID().equals(gp.owner(claim)) && !(boolean)call(gp.repository, "isAdminSubdivision", claim);
    }
    List<Object> owned(UUID player) { return gp.all().stream().filter(c -> parent(c) == null && player.equals(gp.owner(c))).sorted(Comparator.comparingLong(this::id)).toList(); }
    List<Object> children(Object claim) { return gp.all().stream().filter(c -> Objects.equals(parent(c), id(claim))).toList(); }
    List<Object> tree(Object claim) {
        List<Object> result = new ArrayList<>(); result.add(claim);
        for (Object child : children(claim)) result.addAll(tree(child));
        return result;
    }
    String label(Object claim) {
        Object b = call(claim, "bounds");
        return "#" + id(claim) + " [" + call(claim, "worldKey") + ": " + call(b, "minX") + ", " + call(b, "minY") + ", " + call(b, "minZ") + "]";
    }
    // Include descendants: deleting/transferring a newly modified tree needs a fresh confirmation.
    String fingerprint(Object claim) {
        return tree(claim).stream().sorted(Comparator.comparingLong(this::id)).map(c ->
            id(c) + ":" + parent(c) + ":" + gp.owner(c) + ":" + call(c, "worldKey") + ":" + call(c, "bounds")
                + ":" + call(c, "threeDimensional")).toList().toString();
    }
    Object trust(Object claim) { return call(gp.repository, "trustForOrEmpty", claim); }
    @SuppressWarnings("unchecked") Map<String,Object> grants(Object trust) { return (Map<String,Object>)call(trust, "permissionsByIdentifier"); }
    @SuppressWarnings("unchecked") Set<String> identifiers(Object trust, String field) { return (Set<String>)call(trust, field); }
    Set<String> direct(Object claim) {
        Object t = trust(claim); Set<String> result = new LinkedHashSet<>(grants(t).keySet());
        for (String field : List.of("managerIdentifiers", "neighborIdentifiers", "pvpTrustedIdentifiers", "pveTrustedIdentifiers")) result.addAll(identifiers(t, field));
        return result;
    }
    boolean inherits(Object claim) {
        Long p = parent(claim);
        return p != null && parent(gp.byId(p)) == null && !(boolean)call(gp.repository, "isRestrictedSubdivision", claim);
    }
    Object known(MinecraftServer server, String name) { return invokeStatic("FabricPlayerLookup", "find", server, name); }
    String name(MinecraftServer server, String identifier) {
        try { return (String)invokeStatic("FabricPlayerLookup", "name", server, UUID.fromString(identifier)); }
        catch (IllegalArgumentException e) { return identifier; }
    }
    @SuppressWarnings("unchecked")
    void setAccess(Object claim, ServerPlayer actor, Set<String> players, String base, boolean manager, Boolean neighbor) {
        Set<Long> ids = (Set<Long>)call(gp.repository, "inheritingClaimIds", List.of(claim));
        UnaryOperator<Object> edit = old -> {
            Map<String,Object> grants = new LinkedHashMap<>(grants(old));
            Set<String> managers = new LinkedHashSet<>(identifiers(old, "managerIdentifiers"));
            Set<String> neighbors = new LinkedHashSet<>(identifiers(old, "neighborIdentifiers"));
            for (String player : players) {
                if (permission(actor, "managetrust") && managers.contains(player) && !manager && !own(claim, actor))
                    throw new ClaimUiFailure("Tylko właściciel może odebrać Manage Trust.", "Only the owner may revoke Manage Trust.");
                if (base.equals("NONE")) grants.remove(player); else grants.put(player, level(base));
                if (permission(actor, "managetrust")) {
                    if (manager) managers.add(player); else managers.remove(player);
                }
                if (neighbor != null) { if (neighbor) neighbors.add(player); else neighbors.remove(player); }
            }
            try {
                return old.getClass().getConstructor(UUID.class, Map.class, Collection.class, Collection.class, Collection.class, Collection.class, Collection.class)
                    .newInstance(call(old, "ownerId"), grants, managers, neighbors, identifiers(old, "deniedIdentifiers"),
                        identifiers(old, "pvpTrustedIdentifiers"), identifiers(old, "pveTrustedIdentifiers"));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        };
        call(gp.repository, "updateTrust", ids, edit);
    }
    void revoke(Object claim, String player) { call(gp.repository, "revokeTrust", List.of(claim), player); }
    void delete(Object claim, ServerPlayer player) { call(gp.repository, "deleteClaim", id(claim), player); }
    Object balance(UUID player) { return call(gp.repository, "claimBlockBalance", player); }
    void transfer(Object claim, ServerPlayer actor, UUID recipient) {
        Object settings = ClaimGlowIntegration.settings();
        Object transfer = call(settings, "transfer");
        if (!(boolean)call(transfer, "enabled")) throw new ClaimUiFailure("Przekazywanie działek jest wyłączone w GP3D.", "Claim transfers are disabled in GP3D.");
        if (!permission(actor, "transferclaim")) throw new ClaimUiFailure("Brak uprawnienia griefprevention.transferclaim.", "Missing permission: griefprevention.transferclaim.");
        if ((double)call(transfer, "price") > 0 && !permission(actor, "transferclaim.free"))
            throw new ClaimUiFailure("Płatne przekazanie wymaga obsługi ekonomii.", "Paid transfers require economy support.");
        ServerPlayer online = actor.level().getServer().getPlayerList().getPlayer(recipient);
        if (online == null || recipient.equals(actor.getUUID())) throw new ClaimUiFailure("Wybierz innego gracza online.", "Choose another online player.");
        if ((long)(int)call(balance(recipient), "remaining") < ((Number)call(call(claim, "bounds"), "area")).longValue())
            throw new ClaimUiFailure("Odbiorca nie ma wystarczającej liczby bloków działek.", "Recipient has insufficient claim blocks.");
        if ((boolean)call(gp.repository, "isAtClaimCountLimit", recipient, online))
            throw new ClaimUiFailure("Odbiorca osiągnął limit działek.", "Recipient reached the claim count limit.");
        call(gp.repository, "changeOwner", id(claim), recipient, actor);
    }
}
