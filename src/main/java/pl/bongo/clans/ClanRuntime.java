package pl.bongo.clans;

import static pl.bongo.clans.ClanData.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import java.util.*;

public final class ClanRuntime {
    public final ClanService service;
    public final MinecraftServer server;
    public final CatEffect cat;
    private final ClanStore store;
    private final Map<UUID, DeleteRequest> deletes = new HashMap<>();
    private int ticks;
    public static final long DELETE_DELAY_NANOS = 10_000_000_000L;
    public static final long DELETE_TTL_NANOS = 90_000_000_000L;
    public ClanRuntime(MinecraftServer server, ClanStore store) throws java.io.IOException {
        this.server = server; this.store = store; service = new ClanService(store);
        cat = new CatEffect(this);
    }
    public void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.literal(Lang.text(player, "prefix")).withStyle(ChatFormatting.GRAY)
                .append(Lang.component(player, key, args).copy().withStyle(ChatFormatting.WHITE)));
    }
    public void broadcast(Clan clan, String key, Object... args) {
        for (UUID id : clan.members.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) tell(player, key, args);
        }
    }
    public void roleBroadcast(Clan clan, String username, Role role) {
        for (UUID id : clan.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) tell(p, "role_changed", username, Lang.text(p, "role_" + role.name().toLowerCase(Locale.ROOT)));
        }
    }
    public void chat(ServerPlayer sender, String message) {
        Clan c = service.require(sender.getUUID());
        if (message.isBlank()) throw new ClanException("empty_chat");
        for (UUID id : c.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("[" + Lang.text(p, "chat_label") + "] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(CommunityClans.displayName(c, sender.getGameProfile().name()))
                    .append(Component.literal(": " + message).withStyle(ChatFormatting.WHITE)));
        }
    }
    private static String teamName(Clan clan) { return "bcc_" + clan.id.toString().replace("-", "").substring(0, 12); }
    public void syncDisplay() {
        var board = server.getScoreboard();
        // Capture prior teams once, so leaving a clan restores an existing vanilla team.
        Map<UUID, String> backups = new HashMap<>();
        for (Clan clan : service.state().clans.values()) for (Member member : clan.members.values()) {
            User user = service.state().users.get(member.id);
            if (user == null || !user.backedUpTeam) {
                PlayerTeam old = board.getPlayersTeam(member.name);
                backups.put(member.id, old == null || old.getName().startsWith("bcc_") ? null : old.getName());
            }
        }
        if (!backups.isEmpty()) store.update(s -> {
            backups.forEach((id, previous) -> { User u = s.user(id); u.backedUpTeam = true; u.previousTeam = previous; }); return null;
        });
        Set<String> expected = new HashSet<>();
        Map<String, Clan> memberTeams = new HashMap<>();
        for (Clan clan : service.state().clans.values()) {
            String name = teamName(clan); expected.add(name);
            PlayerTeam team = board.getPlayerTeam(name);
            if (team == null) team = board.addPlayerTeam(name);
            team.setDisplayName(Component.literal(clan.name));
            team.setPlayerPrefix(CommunityClans.prefix(clan));
            team.setPlayerSuffix(Component.empty());
            team.setColor(Optional.empty());
            team.setAllowFriendlyFire(true);
            team.setSeeFriendlyInvisibles(false);
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.ALWAYS);
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.ALWAYS);
            for (Member member : clan.members.values()) memberTeams.put(member.name, clan);
        }
        for (PlayerTeam team : new ArrayList<>(board.getPlayerTeams())) if (team.getName().startsWith("bcc_")) {
            if (!expected.contains(team.getName())) board.removePlayerTeam(team);
            else for (String name : new ArrayList<>(team.getPlayers())) {
                Clan c = memberTeams.get(name);
                if (c == null || !team.getName().equals(teamName(c))) board.removePlayerFromTeam(name, team);
            }
        }
        memberTeams.forEach((name, clan) -> {
            PlayerTeam target = board.getPlayerTeam(teamName(clan));
            if (board.getPlayersTeam(name) != target) board.addPlayerToTeam(name, target);
        });
        List<UUID> restored = new ArrayList<>();
        for (var entry : service.state().users.entrySet()) {
            User user = entry.getValue();
            if (!user.backedUpTeam || service.state().clan(entry.getKey()) != null) continue;
            if (user.lastName != null && user.previousTeam != null) {
                PlayerTeam old = board.getPlayerTeam(user.previousTeam);
                if (old != null && board.getPlayersTeam(user.lastName) == null) board.addPlayerToTeam(user.lastName, old);
            }
            restored.add(entry.getKey());
        }
        if (!restored.isEmpty()) store.update(s -> { restored.forEach(id -> { User u = s.user(id); u.backedUpTeam = false; u.previousTeam = null; }); return null; });
        var online = server.getPlayerList().getPlayers();
        if (!online.isEmpty()) server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), online));
    }
    public void requestDelete(ServerPlayer player) {
        Clan c = service.require(player.getUUID());
        if (c.members.get(player.getUUID()).role != Role.FOUNDER) throw new ClanException("no_permission");
        DeleteRequest request = new DeleteRequest(c.id, UUID.randomUUID().toString(), System.nanoTime());
        deletes.put(player.getUUID(), request); showDelete(player, request, 10);
    }
    private static ActionButton button(Component label, String action) {
        return new ActionButton(new CommonButtonData(label, 150), action == null ? Optional.empty()
                : Optional.of(new CustomAll(Identifier.fromNamespaceAndPath(CommunityClans.ID, action), Optional.empty())));
    }
    private void showDelete(ServerPlayer player, DeleteRequest request, int seconds) {
        Clan c = service.state().clans.get(request.clan);
        if (c == null) return;
        var common = new CommonDialogData(Lang.component(player, "delete_title", c.name), Optional.empty(), false, false,
                DialogAction.WAIT_FOR_RESPONSE, List.of(new PlainMessage(Lang.component(player, "delete_body"), 340)), List.of());
        ActionButton confirm = seconds > 0 ? button(Lang.component(player, "delete_countdown", seconds).copy().withStyle(ChatFormatting.GRAY), null)
                : button(Lang.component(player, "delete_confirm").copy().withStyle(ChatFormatting.RED), "confirm/" + request.token);
        player.openDialog(Holder.direct(new MultiActionDialog(common, List.of(confirm),
                Optional.of(button(Lang.component(player, "delete_cancel"), "cancel/" + request.token)), 1)));
        request.lastSeconds = seconds;
    }
    public void forgetDialog(UUID id) { deletes.remove(id); }
    public void tick() {
        cat.tick();
        if (++ticks % 5 != 0) return;
        long now = System.nanoTime();
        for (var entry : new ArrayList<>(deletes.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            DeleteRequest request = entry.getValue();
            Clan c = service.state().clan(entry.getKey());
            if (player == null || c == null || !c.id.equals(request.clan) || c.members.get(entry.getKey()).role != Role.FOUNDER
                    || now - request.started > DELETE_TTL_NANOS) {
                deletes.remove(entry.getKey());
                if (player != null) { player.connection.send(ClientboundClearDialogPacket.INSTANCE); tell(player, "delete_expired"); }
                continue;
            }
            int seconds = (int) Math.max(0, (DELETE_DELAY_NANOS - (now - request.started) + 999_999_999L) / 1_000_000_000L);
            if (seconds != request.lastSeconds) showDelete(player, request, seconds);
        }
    }
    public void click(ServerPlayer player, String path) {
        String[] parts = path.split("/", 2);
        if (parts.length != 2 || !(parts[0].equals("confirm") || parts[0].equals("cancel"))) return;
        DeleteRequest request = deletes.get(player.getUUID());
        if (request == null || !request.token.equals(parts[1]) || System.nanoTime() - request.started > DELETE_TTL_NANOS) {
            tell(player, "delete_expired"); return;
        }
        if (parts[0].equals("cancel")) {
            deletes.remove(player.getUUID()); player.connection.send(ClientboundClearDialogPacket.INSTANCE); tell(player, "delete_cancelled"); return;
        }
        if (System.nanoTime() - request.started < DELETE_DELAY_NANOS) {
            tell(player, "delete_wait"); showDelete(player, request, request.lastSeconds); return;
        }
        try {
            Clan c = service.require(player.getUUID());
            service.delete(player.getUUID(), request.clan);
            deletes.remove(player.getUUID()); player.connection.send(ClientboundClearDialogPacket.INSTANCE);
            broadcast(c, "deleted", c.name); refreshDisplay();
        } catch (ClanException e) { tell(player, e.key); }
        catch (RuntimeException e) { CommunityClans.LOGGER.error("Could not delete clan", e); tell(player, "save_failed"); }
    }
    public void refreshDisplay() {
        try { syncDisplay(); }
        catch (RuntimeException e) { CommunityClans.LOGGER.error("Clan data saved, but display synchronization failed", e); }
        refreshCommands();
    }
    public void refreshCommands() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
    }
    public static final class DeleteRequest {
        public final UUID clan;
        public final String token;
        public final long started;
        int lastSeconds = -1;
        DeleteRequest(UUID clan, String token, long started) { this.clan = clan; this.token = token; this.started = started; }
    }
}
