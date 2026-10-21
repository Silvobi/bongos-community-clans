package pl.bongo.clans.claims;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.*;
import net.minecraft.server.level.ServerPlayer;
import pl.bongo.clans.*;
import static pl.bongo.clans.claims.GpClaims.*;

/** Vanilla dialog menus. Every action is a player-bound, expiring server-side capability. */
public final class ClaimMenus {
    public static ClaimMenus active;
    private static final int PAGE_SIZE = 20;
    private final MinecraftServer server;
    private final ClaimAccess api;
    private final ClaimActionTokens<Consumer<CompoundTag>> actions = new ClaimActionTokens<>();
    private final Map<UUID, PendingDelete> pending = new HashMap<>();
    private record Target(long id, UUID owner, String fingerprint) {}
    private record PendingDelete(List<Target> targets, long started, int lastSecond) {}
    private record Choice(String pl, String en, String plTip, String enTip, String base) {}
    private static final List<Choice> LEVELS = List.of(
        new Choice("Brak podstawowego dostępu", "No interaction access", "Pozostawia tylko osobno zaznaczone prawa zarządzania lub sąsiedztwa.", "Only the separately selected management or neighbor permissions remain.", "NONE"),
        new Choice("Korzystanie", "Basic access", "Przyciski, dźwignie i łóżka — Access Trust.", "Buttons, levers and beds — Access Trust.", "ACCESS"),
        new Choice("Pojemniki", "Containers", "Korzystanie oraz skrzynie, wyposażenie i zwierzęta — Container Trust.", "Basic access plus containers, equipment and animals — Container Trust.", "CONTAINER"),
        new Choice("Budowanie", "Building", "Stawianie i niszczenie bloków oraz niższe poziomy — Build Trust.", "Place and break blocks, plus lower access levels — Build Trust.", "BUILD"));

    public ClaimMenus(MinecraftServer server, GpClaims gp) { this.server = server; this.api = new ClaimAccess(gp); }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(Commands.literal("claims").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (active == null) error(player, "Zarządzanie działkami wymaga GP3D.", "Claim management requires GP3D.");
                else active.overview(player);
                return 1;
            }));
        });
    }
    static boolean pl(ServerPlayer p) { return Lang.language(p.clientInformation().language()).equals("pl"); }
    static String text(ServerPlayer p, String polish, String english) { return pl(p) ? polish : english; }
    static void error(ServerPlayer p, String polish, String english) { p.sendSystemMessage(Component.literal(text(p, polish, english)).withStyle(ChatFormatting.RED)); }
    private static void require(boolean value, String pl, String en) { if (!value) throw new ClaimUiFailure(pl, en); }
    private Target target(Object claim) { return new Target(api.id(claim), api.gp.owner(claim), api.fingerprint(claim)); }
    private Object validate(ServerPlayer player, Target target, boolean owner) {
        Object claim = api.gp.byId(target.id);
        require(claim != null && Objects.equals(target.owner, api.gp.owner(claim)) && target.fingerprint.equals(api.fingerprint(claim)),
            "Działka zmieniła się. Otwórz menu ponownie.", "The claim changed. Reopen the menu.");
        require(owner ? api.own(claim, player) : api.allows(claim, player, "MANAGE"), "Nie masz wymaganych uprawnień do tej działki.", "You no longer have permission to manage this claim.");
        return claim;
    }
    public void forget(UUID player) {
        actions.forget(player); pending.remove(player);
        if (flagsUi != null) call(flagsUi, "forget", player);
    }
    private void clear(ServerPlayer player) { player.connection.send(ClientboundClearDialogPacket.INSTANCE); }
    private List<ActionButton> begin(ServerPlayer p) {
        forget(p.getUUID()); CommunityClans.runtime.forgetDialog(p.getUUID()); return new ArrayList<>();
    }
    private ActionButton button(ServerPlayer p, String label, String tooltip, boolean red, Consumer<CompoundTag> action) {
        Component name = Component.literal(label).withStyle(red ? ChatFormatting.RED : ChatFormatting.WHITE);
        String token = action == null ? null : actions.issue(p.getUUID(), action);
        return new ActionButton(new CommonButtonData(name, Optional.ofNullable(tooltip).map(Component::literal), 360), token == null ? Optional.empty()
            : Optional.of(new CustomAll(Identifier.fromNamespaceAndPath(CommunityClans.ID, "claims/" + token), Optional.empty())));
    }
    private void add(List<ActionButton> buttons, ServerPlayer p, String pl, String en, Consumer<CompoundTag> action) { buttons.add(button(p, text(p, pl, en), null, false, action)); }
    private void show(ServerPlayer p, String title, String body, List<ActionButton> buttons, List<Input> inputs, Consumer<CompoundTag> back) {
        // Vanilla's multi-action codec requires at least one action, including on empty lists/statistics.
        boolean onlyExit = buttons.isEmpty();
        if (onlyExit) buttons.add(button(p, text(p, back == null ? "Zamknij" : "Wróć", back == null ? "Close" : "Back"), null, false, back == null ? data -> clear(p) : back));
        var common = new CommonDialogData(Component.literal(title), Optional.empty(), true, false,
            buttons.stream().anyMatch(b -> b.action().isEmpty()) ? DialogAction.NONE : DialogAction.CLOSE,
            List.of(new PlainMessage(Component.literal(body), 360)), inputs);
        ActionButton exit = button(p, text(p, back == null ? "Zamknij" : "Wróć", back == null ? "Close" : "Back"), null, false, back == null ? data -> clear(p) : back);
        p.openDialog(Holder.direct(new MultiActionDialog(common, buttons, onlyExit ? Optional.empty() : Optional.of(exit), 1)));
    }
    public boolean submit(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        String path = packet.id().getPath();
        if (!path.startsWith("claims/")) return false;
        Consumer<CompoundTag> action = actions.take(player.getUUID(), path.substring(7));
        if (action == null) { error(player, "Formularz wygasł lub został już użyty.", "The form expired or was already used."); return true; }
        forget(player.getUUID());
        try { action.accept(packet.payload().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).orElseGet(CompoundTag::new)); }
        catch (RuntimeException e) {
            clear(player);
            CommunityClans.LOGGER.warn("Claim menu operation failed for {}", player.getUUID(), e);
            failure(player, e);
        }
        return true;
    }
    private static void failure(ServerPlayer player, Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        if (e instanceof ClaimUiFailure f) error(player, f.polish, f.english);
        else error(player, "Nie udało się wykonać operacji. Otwórz menu ponownie; szczegóły zapisano w logu serwera.",
            "The operation failed. Reopen the menu; details were recorded in the server log.");
    }
    public int open(ServerPlayer p) {
        try {
            Object claim = api.at(p);
            require(claim != null, "Nie stoisz na działce.", "You are not standing in a claim.");
            Target target = target(claim); validate(p, target, false);
            Object root = api.root(claim);
            if (!api.children(root).isEmpty()) warning(p, target); else main(p, target);
            return 1;
        } catch (RuntimeException e) { CommunityClans.LOGGER.warn("Could not open claim menu", e); failure(p, e); return 0; }
    }
    private void warning(ServerPlayer p, Target target) {
        Object claim = validate(p, target, false); Object root = api.root(claim);
        List<ActionButton> buttons = begin(p);
        String body = api.parent(claim) == null
            ? text(p, "Ta działka posiada podpodziały — wybrano całą działkę ", "This claim has subdivisions — selected the entire claim ") + api.label(claim) + "."
            : text(p, "Ta działka posiada podpodziały — wybrano podpodział ", "This claim has subdivisions — selected subdivision ") + api.label(claim)
                + text(p, " działki ", " of claim ") + api.label(root) + ".";
        add(buttons, p, "Kontynuuj", "Continue", data -> { validate(p, target, false); main(p, target); });
        add(buttons, p, "Chcę zmienić wybór", "Change selection", data -> {
            Object current = validate(p, target, false); clear(p); ClaimGlowIntegration.show(p, api.root(current));
            p.sendSystemMessage(Component.literal(text(p,
                "Stań w miejscu działki lub podpodziału, który chcesz edytować, powtórz wybór i zatwierdź operację ponownie.",
                "Stand inside the claim or subdivision you want to edit, repeat your selection and confirm the operation again.")));
        });
        show(p, text(p, "Potwierdź wybór działki", "Confirm claim selection"), body, buttons, List.of(), null);
    }
    private void main(ServerPlayer p, Target target) {
        Object claim = validate(p, target, false); List<ActionButton> buttons = begin(p);
        add(buttons, p, "Flagi", "Flags", data -> openFlags(p, target));
        add(buttons, p, "Dodawanie i usuwanie graczy", "Player access", data -> players(p, target));
        if (api.own(claim, p)) buttons.add(button(p, text(p, "Usuń działkę", "Delete claim"), null, true, data -> confirmDelete(p, target)));
        show(p, api.label(claim), text(p, "Menu dotyczy wybranej działki. Przemieszczanie się nie zmienia celu.", "This menu stays bound to the selected claim when you move."), buttons, List.of(), null);
    }
    private void openFlags(ServerPlayer p, Target target) {
        Object claim = validate(p, target, false);
        try {
            Class<?> flags = Class.forName("pl.bongo.bongoutils.claims.ClaimFlags"); Object activeFlags = flags.getField("active").get(null);
            require(activeFlags != null, "Flagi wymagają BongoUtils.", "Flags require BongoUtils.");
            // Discover a freshly created claim before capturing the flag-store revision.
            call(activeFlags, "reconcile");
            Object gp = flags.getField("gp").get(activeFlags); Object store = flags.getField("store").get(activeFlags);
            Class<?> sessionType = Class.forName("pl.bongo.bongoutils.claims.ManageClaimUi$Session");
            Constructor<?> constructor = sessionType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            Object session = constructor.newInstance("", target.id, call(gp, "key", claim), target.owner, call(store, "revision"), System.nanoTime(), null, null, false);
            // Use the live UI instance which receives BongoUtils' click packets.
            require(flagsUi != null, "Otwórz ponownie /manageclaim.", "Reopen /manageclaim.");
            call(flagsUi, "show", p, session, null, null, false);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("BongoUtils claim flag UI is incompatible", e); }
    }
    public static Object flagsUi;
    private void players(ServerPlayer p, Target target) {
        validate(p, target, false); List<ActionButton> buttons = begin(p);
        add(buttons, p, "Dodaj gracza", "Add player", data -> addPlayers(p, target, new LinkedHashSet<>(), 0));
        add(buttons, p, "Usuń gracza", "Remove player", data -> members(p, target, false, 0));
        add(buttons, p, "Zarządzaj permisjami gracza", "Manage player permissions", data -> members(p, target, true, 0));
        show(p, text(p, "Gracze i dostępy", "Players and access"), api.label(validate(p, target, false)), buttons, List.of(), data -> main(p, target));
    }
    private static Input bool(String key, String label, boolean value) { return new Input(key, new BooleanInput(Component.literal(label), value, "true", "false")); }
    private static boolean checked(CompoundTag data, String key) { return data.getBooleanOr(key, false) || data.getStringOr(key, "false").equals("true"); }
    private void addPlayers(ServerPlayer p, Target target, Set<String> selected, int requestedPage) {
        validate(p, target, false);
        List<ServerPlayer> online = server.getPlayerList().getPlayers().stream().filter(other -> !other.getUUID().equals(target.owner))
            .sorted(Comparator.comparing(other -> other.getGameProfile().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (online.size() - 1) / PAGE_SIZE)));
        List<ServerPlayer> visible = online.subList(page * PAGE_SIZE, Math.min(online.size(), (page + 1) * PAGE_SIZE));
        List<ActionButton> buttons = begin(p); List<Input> inputs = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) inputs.add(bool("p" + i, visible.get(i).getGameProfile().name(), selected.contains(visible.get(i).getUUID().toString())));
        inputs.add(new Input("offline", new TextInput(360, Component.literal(text(p, "Nick gracza offline (opcjonalnie)", "Offline player name (optional)")), true, "", 16, Optional.empty())));
        Consumer<CompoundTag> collect = data -> {
            for (int i = 0; i < visible.size(); i++) {
                String id = visible.get(i).getUUID().toString(); if (checked(data, "p" + i)) selected.add(id); else selected.remove(id);
            }
            String name = data.getStringOr("offline", "").trim();
            if (!name.isEmpty()) {
                require(name.matches("[A-Za-z0-9_]{1,16}"), "Niepoprawny nick.", "Invalid player name.");
                Object known = api.known(server, name);
                require(known != null, "Nie znaleziono gracza w lokalnej historii serwera.", "Player not found in the server's local history.");
                selected.add(call(known, "id").toString());
            }
            require(target.owner == null || !selected.contains(target.owner.toString()), "Właściciel ma już pełny dostęp.", "The owner already has full access.");
        };
        add(buttons, p, "Dalej — wybierz uprawnienia", "Next — choose permissions", data -> {
            collect.accept(data); require(!selected.isEmpty(), "Wybierz co najmniej jednego gracza.", "Select at least one player.");
            levels(p, target, Set.copyOf(selected), false);
        });
        if (page > 0) add(buttons, p, "Poprzednia strona", "Previous page", data -> { collect.accept(data); addPlayers(p, target, selected, page - 1); });
        if ((page + 1) * PAGE_SIZE < online.size()) add(buttons, p, "Następna strona", "Next page", data -> { collect.accept(data); addPlayers(p, target, selected, page + 1); });
        show(p, text(p, "Dodaj graczy", "Add players"), text(p, "Zaznacz graczy online lub wpisz nick osoby znanej serwerowi. Wybrano: ", "Select online players or enter a name known to the server. Selected: ")
            + selected.stream().map(id -> api.name(server, id)).toList(), buttons, inputs, data -> players(p, target));
    }
    private Map<String,String> memberSources(Object claim, ServerPlayer viewer) {
        Map<String,String> result = new LinkedHashMap<>();
        for (String id : api.direct(claim)) result.put(id, text(viewer, "bezpośredni", "direct"));
        if (api.inherits(claim)) for (String id : api.direct(api.gp.byId(api.parent(claim)))) result.merge(id, text(viewer, "odziedziczony", "inherited"), (a,b) -> a + ", " + b);
        var state = CommunityClans.runtime.service.state(); var clan = state.clan(api.gp.owner(claim));
        if (clan != null) for (UUID id : clan.members.keySet()) {
            boolean granted = false;
            for (String level : List.of("ACCESS", "CONTAINER", "BUILD"))
                granted |= ClanClaims.extraAccess(api.gp.repository, claim, id, api.level(level));
            if (granted) result.merge(id.toString(), text(viewer, "klanowy", "clan"), (a,b) -> a + ", " + b);
        }
        result.remove(Objects.toString(api.gp.owner(claim)));
        return result;
    }
    private void members(ServerPlayer p, Target target, boolean edit, int requestedPage) {
        Object claim = validate(p, target, false); Map<String,String> sources = memberSources(claim, p);
        List<String> members = sources.keySet().stream().sorted(Comparator.comparing(id -> api.name(server, id), String.CASE_INSENSITIVE_ORDER)).toList();
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (members.size() - 1) / PAGE_SIZE)));
        List<ActionButton> buttons = begin(p);
        for (String id : members.subList(page * PAGE_SIZE, Math.min(members.size(), (page + 1) * PAGE_SIZE)))
            buttons.add(button(p, api.name(server, id) + " • " + sources.get(id), text(p, "Źródła dostępu: ", "Access sources: ") + sources.get(id), false,
                data -> { if (edit) levels(p, target, Set.of(id), true); else confirmRemove(p, target, id); }));
        if (page > 0) add(buttons, p, "Poprzednia strona", "Previous page", data -> members(p, target, edit, page - 1));
        if ((page + 1) * PAGE_SIZE < members.size()) add(buttons, p, "Następna strona", "Next page", data -> members(p, target, edit, page + 1));
        show(p, text(p, edit ? "Zarządzaj permisjami gracza" : "Usuń gracza", edit ? "Manage player permissions" : "Remove player"),
            text(p, "Usunięcie wpisu bezpośredniego nie usuwa dostępu odziedziczonego, publicznego ani klanowego. Jawne odmowy GP3D nadal obowiązują.",
                "Removing a direct entry does not revoke inherited, public or clan access. Native GP3D denials still apply."), buttons, List.of(), data -> players(p, target));
    }
    private void levels(ServerPlayer p, Target target, Set<String> ids, boolean edit) {
        Object claim = validate(p, target, false);
        require(ids.stream().noneMatch(id -> id.startsWith("[")) || api.permission(p, "permissiontrust"), "Wpisy grup wymagają uprawnienia permissiontrust.", "Permission-node entries require permissiontrust.");
        Object trust = api.trust(claim); String first = ids.iterator().next();
        boolean manager = edit && api.identifiers(trust, "managerIdentifiers").contains(first);
        boolean neighbor = edit && api.identifiers(trust, "neighborIdentifiers").contains(first);
        List<ActionButton> buttons = begin(p); List<Input> inputs = new ArrayList<>();
        if (api.permission(p, "managetrust")) inputs.add(bool("manager", text(p, "Nadawanie dostępów innym (Manage Trust)", "Grant access to others (Manage Trust)"), manager));
        if (api.permission(p, "trust")) inputs.add(bool("neighbor", text(p, "Zgoda na bliższe sąsiedztwo (Neighbor Trust)", "Bypass claim distance (Neighbor Trust)"), neighbor));
        for (Choice choice : LEVELS) {
            String permission = switch (choice.base) { case "ACCESS" -> "accesstrust"; case "CONTAINER" -> "containertrust"; case "BUILD" -> "trust"; default -> "untrust"; };
            if (!api.permission(p, permission)) continue;
            String current = edit && Objects.toString(api.grants(trust).get(first), "NONE").equals(choice.base) ? " ✓" : "";
            buttons.add(button(p, text(p, choice.pl, choice.en) + current, text(p, choice.plTip, choice.enTip), false, data -> {
                Object actual = validate(p, target, false);
                require(api.permission(p, permission), "Uprawnienie do tej operacji zostało odebrane.", "Command permission was revoked.");
                boolean manage = api.permission(p, "managetrust") ? checked(data, "manager") : manager;
                require(!ids.contains("public") || !manage, "Nie można nadać publicznego zarządzania.", "Public management is not allowed.");
                require(ids.stream().noneMatch(id -> id.startsWith("[")) || api.permission(p, "permissiontrust"), "Brak uprawnienia permissiontrust.", "Missing permissiontrust permission.");
                api.setAccess(actual, p, ids, choice.base, manage, api.permission(p, "trust") ? checked(data, "neighbor") : null);
                p.sendSystemMessage(Component.literal(text(p, "Zapisano uprawnienia dla: ", "Saved permissions for: ") + ids.stream().map(id -> api.name(server, id)).toList()));
                players(p, target);
            }));
        }
        show(p, text(p, "Wybierz poziom dostępu", "Choose access level"), ids.stream().map(id -> api.name(server, id)).toList()
            + "\n" + text(p, "Opis poziomu zobaczysz po najechaniu. Manage Trust w GP3D obejmuje również budowanie. Dostęp odziedziczony i klanowy pozostaje niezależny.",
                "Hover for details. GP3D Manage Trust also allows building. Inherited and clan access remain independent."), buttons, inputs, data -> players(p, target));
    }
    private void confirmRemove(ServerPlayer p, Target target, String id) {
        Object claim = validate(p, target, false); List<ActionButton> buttons = begin(p);
        if (api.direct(claim).contains(id)) buttons.add(button(p, text(p, "Tak, usuń dostęp bezpośredni", "Yes, remove direct access"), null, true, data -> {
            Object actual = validate(p, target, false);
            require(api.permission(p, "untrust"), "Brak uprawnienia untrust.", "Missing untrust permission.");
            require(!api.identifiers(api.trust(actual), "managerIdentifiers").contains(id) || api.own(actual, p),
                "Tylko właściciel może odebrać Manage Trust.", "Only the owner may revoke Manage Trust.");
            api.revoke(actual, id); members(p, target, false, 0);
        }));
        show(p, text(p, "Czy na pewno usunąć gracza?", "Remove this player?"), api.name(server, id) + "\n" + api.label(claim) + "\n"
            + text(p, "Usuwane są bezpośrednie trusty na wybranym terenie i jego podziałach. Dostęp odziedziczony, publiczny lub klanowy może nadal obowiązywać. Jeśli nie ma przycisku potwierdzenia, dostęp pochodzi wyłącznie z innych źródeł.",
                "Direct trust on this claim and its subdivisions is removed. Inherited, public or clan access may remain. If no confirmation button appears, access comes only from other sources."), buttons, List.of(), data -> members(p, target, false, 0));
    }
    public void overview(ServerPlayer p) {
        List<ActionButton> buttons = begin(p);
        add(buttons, p, "Usuń działkę", "Delete claim", data -> ownedList(p, false, 0));
        add(buttons, p, "Usuń wszystkie działki", "Delete all claims", data -> startDeleteAll(p));
        add(buttons, p, "Oddaj działkę", "Give away a claim", data -> ownedList(p, true, 0));
        add(buttons, p, "Statystyki", "Statistics", data -> statistics(p));
        show(p, text(p, "Moje działki", "My claims"), text(p, "Zarządzaj wszystkimi własnymi działkami.", "Manage all claims you own."), buttons, List.of(), null);
    }
    private void ownedList(ServerPlayer p, boolean transfer, int requestedPage) {
        List<Object> claims = api.owned(p.getUUID()); List<ActionButton> buttons = begin(p);
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (claims.size() - 1) / PAGE_SIZE)));
        for (Object claim : claims.subList(page * PAGE_SIZE, Math.min(claims.size(), (page + 1) * PAGE_SIZE))) {
            Target target = target(claim);
            buttons.add(button(p, api.label(claim), null, false, data -> { if (transfer) recipient(p, target, 0); else confirmDelete(p, target); }));
        }
        if (page > 0) add(buttons, p, "Poprzednia strona", "Previous page", data -> ownedList(p, transfer, page - 1));
        if ((page + 1) * PAGE_SIZE < claims.size()) add(buttons, p, "Następna strona", "Next page", data -> ownedList(p, transfer, page + 1));
        show(p, text(p, transfer ? "Oddaj działkę" : "Usuń działkę", transfer ? "Give away a claim" : "Delete claim"),
            text(p, "Liczba działek: ", "Claim count: ") + claims.size(), buttons, List.of(), data -> overview(p));
    }
    private void confirmDelete(ServerPlayer p, Target target) {
        Object claim = validate(p, target, true); List<ActionButton> buttons = begin(p);
        String permission = api.children(claim).isEmpty() ? "abandonclaim" : "abandontoplevelclaim";
        buttons.add(button(p, text(p, "Tak, usuń działkę", "Yes, delete claim"), null, true, data -> {
            Object actual = validate(p, target, true); require(api.permission(p, permission), "Brak uprawnienia usuwania.", "Missing abandonment permission.");
            api.delete(actual, p); overview(p);
        }));
        show(p, text(p, "Czy na pewno usunąć działkę?", "Delete this claim?"), api.label(claim) + "\n"
            + text(p, "Usunięte zostaną także wszystkie jej podpodziały. Bloki i budynki pozostaną, ale stracą ochronę.",
                "All its subdivisions will also be deleted. Blocks and buildings remain but lose claim protection."), buttons, List.of(), data -> overview(p));
    }
    private void startDeleteAll(ServerPlayer p) {
        List<Target> targets = api.owned(p.getUUID()).stream().map(this::target).toList();
        require(!targets.isEmpty(), "Nie masz działek.", "You have no claims.");
        showDeleteAll(p, new PendingDelete(targets, System.nanoTime(), -1));
    }
    private void showDeleteAll(ServerPlayer p, PendingDelete request) {
        long elapsed = System.nanoTime() - request.started;
        int seconds = (int)Math.max(0, (ClanRuntime.DELETE_DELAY_NANOS - elapsed + 999_999_999L) / 1_000_000_000L);
        List<ActionButton> buttons = begin(p);
        buttons.add(button(p, seconds > 0 ? text(p, "Zatwierdź za ", "Confirm in ") + seconds + " s" : text(p, "Tak, usuń wszystkie działki", "Yes, delete all claims"), null, seconds == 0,
            seconds > 0 ? null : data -> {
                require(System.nanoTime() - request.started >= ClanRuntime.DELETE_DELAY_NANOS, "Poczekaj na zakończenie odliczania.", "Wait for the countdown.");
                require(api.permission(p, "abandonallclaims"), "Brak uprawnienia usuwania wszystkich działek.", "Missing abandon-all permission.");
                List<Target> current = api.owned(p.getUUID()).stream().map(this::target).toList();
                require(current.equals(request.targets), "Lista działek zmieniła się. Rozpocznij ponownie.", "Your claims changed. Start again.");
                for (Target target : request.targets) api.delete(validate(p, target, true), p);
                overview(p);
            }));
        show(p, text(p, "Czy na pewno usunąć wszystkie działki?", "Delete ALL your claims?"),
            text(p, "Działki i wszystkie ich podpodziały stracą ochronę. Liczba działek: ", "Claims and all their subdivisions will lose protection. Claim count: ") + request.targets.size()
                + "\n" + request.targets.stream().map(t -> api.label(api.gp.byId(t.id))).toList(), buttons, List.of(), data -> overview(p));
        pending.put(p.getUUID(), new PendingDelete(request.targets, request.started, seconds));
    }
    public void tick() {
        actions.expire();
        for (var entry : new ArrayList<>(pending.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey()); PendingDelete request = entry.getValue();
            if (player == null || System.nanoTime() - request.started >= ClaimActionTokens.TTL) { forget(entry.getKey()); continue; }
            int seconds = (int)Math.max(0, (ClanRuntime.DELETE_DELAY_NANOS - (System.nanoTime() - request.started) + 999_999_999L) / 1_000_000_000L);
            if (seconds != request.lastSecond) {
                try {
                    require(api.owned(player.getUUID()).stream().map(this::target).toList().equals(request.targets), "Lista działek zmieniła się.", "Claims changed.");
                    showDeleteAll(player, request);
                } catch (RuntimeException e) { forget(entry.getKey()); clear(player); failure(player, e); }
            }
        }
    }
    private void recipient(ServerPlayer p, Target target, int requestedPage) {
        Object claim = validate(p, target, true); List<ActionButton> buttons = begin(p);
        List<ServerPlayer> online = server.getPlayerList().getPlayers().stream().filter(other -> !other.getUUID().equals(p.getUUID()))
            .sorted(Comparator.comparing(other -> other.getGameProfile().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (online.size() - 1) / PAGE_SIZE)));
        for (ServerPlayer other : online.subList(page * PAGE_SIZE, Math.min(online.size(), (page + 1) * PAGE_SIZE))) {
            UUID id = other.getUUID(); String name = other.getGameProfile().name();
            buttons.add(button(p, name, null, false, data -> confirmTransfer(p, target, id, name)));
        }
        if (page > 0) add(buttons, p, "Poprzednia strona", "Previous page", data -> recipient(p, target, page - 1));
        if ((page + 1) * PAGE_SIZE < online.size()) add(buttons, p, "Następna strona", "Next page", data -> recipient(p, target, page + 1));
        show(p, text(p, "Wybierz odbiorcę", "Choose recipient"), api.label(claim), buttons, List.of(), data -> ownedList(p, true, 0));
    }
    private void confirmTransfer(ServerPlayer p, Target target, UUID recipient, String name) {
        Object claim = validate(p, target, true); List<ActionButton> buttons = begin(p);
        buttons.add(button(p, text(p, "Tak, oddaj działkę", "Yes, give away claim"), null, true, data -> {
            Object actual = validate(p, target, true); api.transfer(actual, p, recipient);
            ServerPlayer receiver = server.getPlayerList().getPlayer(recipient);
            if (receiver != null) receiver.sendSystemMessage(Component.literal(text(receiver, "Otrzymano działkę: ", "You received claim: ") + api.label(actual)));
            overview(p);
        }));
        show(p, text(p, "Czy na pewno oddać działkę?", "Give away this claim?"), api.label(claim) + "\n" + text(p, "Nowy właściciel: ", "New owner: ") + name
            + "\n" + text(p, "Przekazujesz całą działkę wraz z podpodziałami i tracisz prawa właściciela.", "The entire claim and its subdivisions change owner. You lose owner permissions."), buttons, List.of(), data -> recipient(p, target, 0));
    }
    private void statistics(ServerPlayer p) {
        Object balance = api.balance(p.getUUID()); List<ActionButton> buttons = begin(p);
        String body = text(p, "Łączna powierzchnia do wykorzystania: ", "Total claim-block entitlement: ") + call(balance, "totalEntitlement") + " m²\n"
            + text(p, "Wykorzystana powierzchnia: ", "Used area: ") + call(balance, "claimedArea") + " m²\n"
            + text(p, "Dostępna powierzchnia: ", "Available area: ") + call(balance, "remaining") + " m²\n"
            + text(p, "Liczba działek: ", "Claim count: ") + api.owned(p.getUUID()).size();
        show(p, text(p, "Statystyki działek", "Claim statistics"), body, buttons, List.of(), data -> overview(p));
    }
}
