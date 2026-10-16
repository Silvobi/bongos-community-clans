package pl.bongo.clans;

import static net.minecraft.commands.Commands.*;
import static pl.bongo.clans.ClanData.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class ClanCommands {
    @FunctionalInterface private interface Action { void run(ClanRuntime runtime, ServerPlayer player); }
    private static int run(CommandContext<CommandSourceStack> ctx, Action action) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) { ctx.getSource().sendFailure(Component.literal("This command is available to players. / Ta komenda jest dostępna dla graczy.")); return 0; }
        if (CommunityClans.runtime == null) { p.sendSystemMessage(Lang.component(p, "unavailable")); return 0; }
        try { action.run(CommunityClans.runtime, p); return 1; }
        catch (ClanException e) { CommunityClans.runtime.tell(p, e.key); return 0; }
        catch (RuntimeException e) { CommunityClans.LOGGER.error("Clan command failed", e); CommunityClans.runtime.tell(p, "save_failed"); return 0; }
    }
    private static String str(CommandContext<CommandSourceStack> c, String name) { return StringArgumentType.getString(c, name); }
    static String textArgument(String value) {
        if (value.startsWith("\"")) {
            try { StringReader reader = new StringReader(value); String result = reader.readString(); if (!reader.canRead()) return result; }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { /* A literal quote is valid in a raw Unicode tag. */ }
        }
        return value;
    }
    static String[] createArguments(String raw) {
        int separator = 0;
        while (separator < raw.length() && !Character.isWhitespace(raw.charAt(separator))) separator++;
        if (separator == raw.length()) throw new ClanException("create_usage");
        String name = raw.substring(0, separator);
        while (separator < raw.length() && Character.isWhitespace(raw.charAt(separator))) separator++;
        if (separator == raw.length()) throw new ClanException("create_usage");
        return new String[] { textArgument(name), textArgument(raw.substring(separator)) };
    }
    private static boolean hasPower(CommandSourceStack source, int power) {
        if (source.getPlayer() == null || CommunityClans.runtime == null) return false;
        Clan c = CommunityClans.runtime.service.state().clan(source.getPlayer().getUUID());
        return c != null && c.members.get(source.getPlayer().getUUID()).role.power >= power;
    }
    private static RequiredArgumentBuilder<CommandSourceStack, String> username(boolean online) {
        return argument("username", StringArgumentType.word()).suggests((ctx, builder) -> {
            if (online) return SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder);
            var runtime = CommunityClans.runtime; var player = ctx.getSource().getPlayer();
            Clan c = runtime == null || player == null ? null : runtime.service.state().clan(player.getUUID());
            return SharedSuggestionProvider.suggest(c == null ? List.of() : c.members.values().stream().map(m -> m.name).toList(), builder);
        });
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = literal("clan").executes(c -> run(c, (r,p) -> help(r,p)));
        root.then(literal("cat").executes(c -> run(c, (r,p) -> r.cat.request(p))));
        root.then(literal("help").executes(c -> run(c, (r,p) -> help(r,p))));
        root.then(literal("create").then(argument("name_tag", StringArgumentType.greedyString())
                .executes(c -> run(c, (r,p) -> {
                    String[] parts=createArguments(str(c,"name_tag"));
                    r.service.create(p.getUUID(), p.getGameProfile().name(), parts[0],parts[1]);
                    Clan clan = r.service.require(p.getUUID()); r.tell(p,"created",clan.name,CommunityClans.bracketedTag(clan)); r.refreshDisplay();
                }))));
        root.then(literal("invite").requires(s -> hasPower(s,1)).then(username(true).executes(c -> run(c,(r,p) -> {
            ServerPlayer target = r.server.getPlayerList().getPlayerByName(str(c,"username"));
            if (target == null) throw new ClanException("player_offline");
            r.service.invite(p.getUUID(),target.getUUID()); Clan clan = r.service.require(p.getUUID());
            r.tell(p,"invited",target.getGameProfile().name()); r.tell(target,"invitation",p.getGameProfile().name(),clan.name,CommunityClans.bracketedTag(clan));
        }))));
        root.then(literal("accept").executes(c -> run(c,(r,p) -> {
            r.service.accept(p.getUUID(),p.getGameProfile().name()); r.broadcast(r.service.require(p.getUUID()),"accepted",p.getGameProfile().name()); r.refreshDisplay();
        })));
        root.then(literal("decline").executes(c -> run(c,(r,p) -> { r.service.decline(p.getUUID()); r.tell(p,"declined"); })));
        root.then(literal("ignore").executes(c -> run(c,(r,p) -> r.tell(p,r.service.ignore(p.getUUID()) ? "ignore_on" : "ignore_off"))));
        root.then(literal("quickchat").executes(c -> run(c,(r,p) -> r.tell(p,r.service.quickchat(p.getUUID()) ? "quick_on" : "quick_off"))));
        root.then(literal("chat").then(argument("message",StringArgumentType.greedyString()).executes(c -> run(c,(r,p) -> r.chat(p,str(c,"message"))))));
        root.then(literal("leave").executes(c -> run(c,(r,p) -> {
            Clan clan = r.service.require(p.getUUID()); r.service.leave(p.getUUID());
            r.tell(p,"you_left",clan.name); r.broadcast(r.service.state().clans.get(clan.id),"left",p.getGameProfile().name()); r.refreshDisplay();
        })));
        root.then(literal("kick").requires(s -> hasPower(s,1)).then(username(false).executes(c -> run(c,(r,p) -> {
            Clan clan = r.service.require(p.getUUID()); UUID target = r.service.member(p.getUUID(),str(c,"username"));
            String name = clan.members.get(target).name; r.service.kick(p.getUUID(),target);
            ServerPlayer t = r.server.getPlayerList().getPlayer(target); if (t != null) r.tell(t,"you_kicked",clan.name);
            r.broadcast(r.service.state().clans.get(clan.id),"kicked",p.getGameProfile().name(),name); r.refreshDisplay();
        }))));
        for (String action : List.of("promote","demote")) root.then(literal(action).requires(s -> hasPower(s,2)).then(username(false)
                .then(argument("class",StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(
                        hasPower(c.getSource(),3) ? List.of("cofounder","manager","member") : List.of("manager","member"),b))
                .executes(c -> run(c,(r,p) -> {
                    UUID target = r.service.member(p.getUUID(),str(c,"username")); Role role = Role.parse(str(c,"class"));
                    r.service.role(p.getUUID(),target,role,action.equals("promote"));
                    Clan clan = r.service.require(p.getUUID()); r.roleBroadcast(clan,clan.members.get(target).name,role);
                    r.refreshCommands();
                })))));
        root.then(literal("transfer").requires(s -> hasPower(s,3)).then(username(false).executes(c -> run(c,(r,p) -> {
            UUID target = r.service.member(p.getUUID(),str(c,"username")); r.service.transfer(p.getUUID(),target);
            Clan clan = r.service.require(p.getUUID()); r.broadcast(clan,"transfer_done",clan.members.get(target).name); r.refreshCommands();
        }))));
        var change = literal("change").requires(s -> hasPower(s,2));
        for (String field : List.of("name","tag")) change.then(literal(field)
                .then(argument("value",StringArgumentType.greedyString())
                .executes(c -> run(c,(r,p) -> {
                    r.service.change(p.getUUID(),field,textArgument(str(c,"value"))); Clan clan = r.service.require(p.getUUID());
                    Object value = field.equals("name") ? clan.name : CommunityClans.bracketedTag(clan);
                    r.broadcast(clan,"changed_" + field,value); r.refreshDisplay();
                }))));
        root.then(change);
        root.then(literal("info").executes(c -> run(c,(r,p) -> ClanInfoMenu.open(r,p,0)))
                .then(argument("page",IntegerArgumentType.integer(1)).executes(c -> run(c,(r,p) -> ClanInfoMenu.open(r,p,IntegerArgumentType.getInteger(c,"page")-1)))));
        root.then(literal("delete").requires(s -> hasPower(s,3)).executes(c -> run(c,(r,p) -> r.requestDelete(p))));
        dispatcher.register(root);
    }
    private static void help(ClanRuntime r, ServerPlayer p) {
        for (String key : List.of("help_title","help_create","help_members","help_roles","help_change","help_chat","help_info","help_ranks")) r.tell(p,key);
    }
}
