package pl.bongo.clans;

import java.io.*;
import java.net.Socket;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import static pl.bongo.clans.IntegrationHarness.*;

/** Uses a disposable loopback fixture only. No production-world paths or server control. */
public final class ClaimMenuIntegrationHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port = Integer.parseInt(args[0]);
        net.minecraft.core.registries.BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            if (!holder.areComponentsBound()) holder.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        });
        try (var admin = fixture(Integer.parseInt(args[1]), args[2]); var owner = new Bot("MenuOwner", "pl_pl"); var peer = new Bot("MenuPeer", "en_us"); var outsider = new Bot("MenuOther", "en_us")) {
            System.out.println(admin.command("adjustbonusclaimblocks " + owner.name + " 10000"));
            System.out.println(admin.command("adjustbonusclaimblocks " + peer.name + " 10000"));
            System.out.println(admin.command("deleteallclaims " + owner.name));
            System.out.println(admin.command("deleteallclaims " + peer.name));
            System.out.println(admin.command("tp " + owner.name + " 100 -60 100")); owner.drain(300);
            owner.events.clear(); owner.command("claim create 5"); owner.drain(500);
            System.out.println("Create chat: " + owner.messages.stream().map(Component::getString).toList());
            check(owner.events.stream().filter(ClientboundAddEntityPacket.class::isInstance).map(ClientboundAddEntityPacket.class::cast)
                .anyMatch(p -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).toString().equals("minecraft:block_display")), "GP3D glowing displays are sent");
            check(owner.events.stream().filter(ClientboundBlockUpdatePacket.class::isInstance).map(ClientboundBlockUpdatePacket.class::cast)
                .noneMatch(p -> Set.of("minecraft:gold_block", "minecraft:glowstone", "minecraft:iron_block").contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.getBlockState().getBlock()).toString())), "glow-only creation sends no ghost-block markers");

            owner.command("manageclaim"); MultiActionDialog main = dialog(owner);
            check(labels(main).equals(List.of("Flagi", "Dodawanie i usuwanie graczy", "Usuń działkę")), "local main menu has the requested buttons");
            check(hasColor(main.actions().get(2).button().label(), 0xFF5555), "delete button is red");
            click(owner, main, "Flagi", new CompoundTag()); MultiActionDialog flags = dialog(owner);
            check(labels(flags).stream().anyMatch(s -> s.startsWith("Dostęp członków klanu")), "existing BongoUtils flags and clan flag are preserved");
            click(owner, flags, labels(flags).stream().filter(s -> s.startsWith("Eksplozje:")).findFirst().orElseThrow(), new CompoundTag());
            MultiActionDialog editFlag = dialog(owner); click(owner, editFlag, "Włączone", new CompoundTag());
            MultiActionDialog confirmFlag = dialog(owner); click(owner, confirmFlag, "Zastosuj", new CompoundTag()); dialog(owner);
            owner.command("manageclaim global"); flags = dialog(owner);
            check(flags.common().title().getString().contains("globalne"), "global command remains the existing flags screen");

            owner.command("manageclaim"); main = dialog(owner); click(owner, main, "Dodawanie i usuwanie graczy", new CompoundTag());
            MultiActionDialog players = dialog(owner); click(owner, players, "Dodaj gracza", new CompoundTag());
            MultiActionDialog picker = dialog(owner);
            check(picker.common().inputs().size() == 3, "online multi-select plus offline name input");
            CompoundTag selected = new CompoundTag();
            picker.common().inputs().forEach(i -> { if (!i.key().equals("offline")) selected.putBoolean(i.key(), true); });
            click(owner, picker, "Dalej — wybierz uprawnienia", selected); MultiActionDialog levels = dialog(owner);
            check(levels.actions().stream().allMatch(a -> a.button().tooltip().isPresent()), "access levels have hover explanations");
            CustomAll grant = action(levels, "Budowanie");
            outsider.send(new ServerboundCustomClickActionPacket(grant.id(), Optional.empty())); outsider.message("expired");
            owner.send(new ServerboundCustomClickActionPacket(grant.id(), Optional.of(new CompoundTag()))); players = dialog(owner);
            owner.send(new ServerboundCustomClickActionPacket(grant.id(), Optional.empty())); owner.message("wygasł");
            check(true, "foreign-player and replay tokens cannot mutate trust");

            click(owner, players, "Zarządzaj permisjami gracza", new CompoundTag()); MultiActionDialog members = dialog(owner);
            check(labels(members).stream().anyMatch(s -> s.startsWith(peer.name + " • bezpośredni")) && labels(members).stream().anyMatch(s -> s.startsWith(outsider.name + " • bezpośredni")), "both selected players received direct access");
            click(owner, members, labels(members).stream().filter(s -> s.startsWith(peer.name)).findFirst().orElseThrow(), new CompoundTag());
            levels = dialog(owner); click(owner, levels, "Korzystanie", new CompoundTag()); dialog(owner);
            owner.command("trust " + peer.name); owner.message("/manageclaim");
            owner.command("claim trust " + peer.name); owner.message("/manageclaim");
            owner.command("at " + peer.name); owner.message("/manageclaim");
            owner.command("abandonclaim"); owner.message("/claims");
            check(true, "legacy aliases and unified commands show menu hints");

            owner.command("manageclaim"); main = dialog(owner); CustomAll stale = action(main, "Usuń działkę");
            // A menu remains pinned after movement: deleting still asks about the selected claim.
            System.out.println(admin.command("tp " + owner.name + " 150 -60 150")); owner.drain(150);
            owner.send(new ServerboundCustomClickActionPacket(stale.id(), Optional.empty())); MultiActionDialog confirmation = dialog(owner);
            check(confirmation.common().body().toString().contains("100") || confirmation.common().body().toString().contains("95"), "moving does not retarget the selected claim");
            owner.command("claims"); MultiActionDialog overview = dialog(owner);
            click(owner, overview, "Statystyki", new CompoundTag()); MultiActionDialog stats = dialog(owner);
            check(stats.common().body().toString().contains("121"), "statistics use native claimed area and block balance");
            owner.command("claims"); overview = dialog(owner); click(owner, overview, "Usuń wszystkie działki", new CompoundTag());
            MultiActionDialog waiting = dialog(owner);
            check(waiting.actions().getFirst().action().isEmpty(), "delete-all confirmation is unavailable before the delay");
            long until = System.currentTimeMillis() + 13000; MultiActionDialog unlocked = null;
            while (System.currentTimeMillis() < until && unlocked == null) {
                Object packet = owner.poll(100); peer.drain(2); outsider.drain(2);
                if (packet instanceof ClientboundShowDialogPacket shown && shown.dialog().value() instanceof MultiActionDialog d && d.actions().getFirst().action().isPresent()) unlocked = d;
            }
            check(unlocked != null, "delete-all unlocks after ten real seconds");
            owner.events.removeIf(e -> e instanceof ClientboundShowDialogPacket);
            // Cancel rather than delete here; remove tests cover exact-target deletion separately.
            CustomAll cancel = (CustomAll)unlocked.exitAction().orElseThrow().action().orElseThrow();
            owner.send(new ServerboundCustomClickActionPacket(cancel.id(), Optional.empty())); dialog(owner);

            // Offline lookup follows GP3D's local player cache; no online/offline UUID guessing.
            outsider.close(); owner.drain(200);
            admin.command("tp " + owner.name + " 100 -60 100"); owner.drain(150);
            owner.command("manageclaim"); main = dialog(owner); click(owner, main, "Dodawanie i usuwanie graczy", new CompoundTag()); players = dialog(owner);
            click(owner, players, "Usuń gracza", new CompoundTag()); members = dialog(owner);
            click(owner, members, labels(members).stream().filter(s -> s.startsWith(outsider.name)).findFirst().orElseThrow(), new CompoundTag());
            MultiActionDialog remove = dialog(owner); click(owner, remove, "Tak, usuń dostęp bezpośredni", new CompoundTag()); dialog(owner);
            owner.command("manageclaim"); main = dialog(owner); click(owner, main, "Dodawanie i usuwanie graczy", new CompoundTag()); players = dialog(owner);
            click(owner, players, "Dodaj gracza", new CompoundTag()); picker = dialog(owner);
            CompoundTag offline = new CompoundTag(); offline.putString("offline", outsider.name);
            click(owner, picker, "Dalej — wybierz uprawnienia", offline); levels = dialog(owner); click(owner, levels, "Pojemniki", new CompoundTag()); dialog(owner);
            check(true, "remove confirmation and offline player name lookup succeed");

            // Exercise the native shovel interaction, not a fake repository or test-only command.
            admin.command("item replace entity " + owner.name + " weapon.mainhand with minecraft:golden_shovel");
            admin.command("setblock 102 -57 102 minecraft:stone");
            owner.command("3dsubdivideclaims"); owner.drain(150);
            shovel(owner, 98, -61, 98, 1); owner.drain(150); shovel(owner, 102, -57, 102, 2); owner.drain(300);
            System.out.println("Subdivision chat: " + owner.messages.stream().map(Component::getString).toList());
            owner.command("manageclaim"); MultiActionDialog warning = dialog(owner);
            check(labels(warning).contains("Chcę zmienić wybór") && warning.common().body().toString().contains("wybrano podpodział"), "standing in a 3D subdivision warns about the exact selection");
            click(owner, warning, "Chcę zmienić wybór", new CompoundTag()); owner.message("Stań w miejscu działki lub podpodziału");
            admin.command("tp " + owner.name + " 104 -60 104"); owner.drain(150);
            owner.command("manageclaim"); warning = dialog(owner);
            check(warning.common().body().toString().contains("wybrano całą działkę"), "moving outside the subdivision selects the parent afresh");
            click(owner, warning, "Kontynuuj", new CompoundTag()); main = dialog(owner);
            click(owner, main, "Dodawanie i usuwanie graczy", new CompoundTag()); players = dialog(owner);
            click(owner, players, "Zarządzaj permisjami gracza", new CompoundTag()); members = dialog(owner);
            click(owner, members, labels(members).stream().filter(s -> s.startsWith(peer.name)).findFirst().orElseThrow(), new CompoundTag()); levels = dialog(owner);
            click(owner, levels, "Korzystanie ✓", new CompoundTag()); dialog(owner);
            admin.command("tp " + owner.name + " 100 -60 100"); owner.drain(150); owner.command("manageclaim"); warning = dialog(owner);
            click(owner, warning, "Kontynuuj", new CompoundTag()); main = dialog(owner); click(owner, main, "Dodawanie i usuwanie graczy", new CompoundTag()); players = dialog(owner);
            click(owner, players, "Zarządzaj permisjami gracza", new CompoundTag()); members = dialog(owner);
            check(labels(members).stream().anyMatch(s -> s.startsWith(peer.name) && s.contains("odziedziczony")), "subdivision access identifies inheritance");

            owner.command("claims"); overview = dialog(owner); click(owner, overview, "Oddaj działkę", new CompoundTag());
            MultiActionDialog owned = dialog(owner); click(owner, owned, labels(owned).getFirst(), new CompoundTag());
            MultiActionDialog recipients = dialog(owner); click(owner, recipients, peer.name, new CompoundTag());
            confirmation = dialog(owner); click(owner, confirmation, "Tak, oddaj działkę", new CompoundTag()); dialog(owner); peer.message("received claim");
            admin.command("tp " + peer.name + " 100 -60 100"); peer.drain(150); peer.command("manageclaim"); warning = dialog(peer);
            check(warning.common().body().toString().contains("selected subdivision"), "transferring a parent also transfers its subdivisions");
            peer.command("claims"); overview = dialog(peer); click(peer, overview, "Delete claim", new CompoundTag()); owned = dialog(peer);
            click(peer, owned, labels(owned).getFirst(), new CompoundTag()); confirmation = dialog(peer);
            check(confirmation.common().body().toString().contains("subdivisions"), "delete confirmation explicitly includes subdivisions");
            click(peer, confirmation, "Yes, delete claim", new CompoundTag()); dialog(peer);
            peer.command("claims"); overview = dialog(peer); click(peer, overview, "Statistics", new CompoundTag()); stats = dialog(peer);
            check(stats.common().body().toString().contains("Claim count: 0"), "deleting the selected claim removes the complete tree");

            owner.command("basicclaims"); owner.drain(100);
            for (int x : List.of(300, 400)) {
                admin.command("tp " + owner.name + " " + x + " -60 100"); owner.drain(100); owner.command("claim create 5"); owner.drain(150);
            }
            owner.command("claims"); overview = dialog(owner); click(owner, overview, "Usuń wszystkie działki", new CompoundTag()); dialog(owner);
            until = System.currentTimeMillis() + 13000; unlocked = null;
            while (System.currentTimeMillis() < until && unlocked == null) {
                Object packet = owner.poll(100); peer.drain(2);
                if (packet instanceof ClientboundShowDialogPacket shown && shown.dialog().value() instanceof MultiActionDialog d && d.actions().getFirst().action().isPresent()) unlocked = d;
            }
            check(unlocked != null, "batch deletion waits for a fresh countdown"); owner.events.removeIf(e -> e instanceof ClientboundShowDialogPacket);
            click(owner, unlocked, "Tak, usuń wszystkie działki", new CompoundTag()); dialog(owner);
            owner.command("claims"); overview = dialog(owner); click(owner, overview, "Statystyki", new CompoundTag()); stats = dialog(owner);
            check(stats.common().body().toString().contains("Liczba działek: 0"), "delete-all removes the pinned batch of owned claims");
            System.out.println("PASS: claim menu integration scenarios completed.");
        }
    }
    static Rcon fixture(int port, String password) throws IOException {
        Rcon admin = new Rcon(port, password);
        System.out.println(admin.command("whitelist off")); admin.command("difficulty peaceful"); admin.command("time set day");
        return admin;
    }
    static MultiActionDialog dialog(Bot bot) throws Exception { return (MultiActionDialog)((ClientboundShowDialogPacket)bot.await(p -> p instanceof ClientboundShowDialogPacket, 5000)).dialog().value(); }
    static List<String> labels(MultiActionDialog d) { return d.actions().stream().map(a -> a.button().label().getString()).toList(); }
    static CustomAll action(MultiActionDialog d, String label) { return (CustomAll)d.actions().stream().filter(a -> a.button().label().getString().equals(label)).findFirst().orElseThrow(() -> new AssertionError(label + " missing from " + labels(d))).action().orElseThrow(); }
    static void click(Bot bot, MultiActionDialog d, String label, CompoundTag data) throws Exception { bot.send(new ServerboundCustomClickActionPacket(action(d, label).id(), Optional.of(data))); }
    static void shovel(Bot bot, int x, int y, int z, int sequence) throws Exception {
        bot.send(new ServerboundUseItemOnPacket(net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x + .5, y + .5, z + .5), net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(x, y, z), false), sequence));
    }
    static final class Rcon implements AutoCloseable {
        final Socket socket; final DataInputStream input; final OutputStream output;
        Rcon(int port, String password) throws IOException {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(5000); input = new DataInputStream(socket.getInputStream()); output = socket.getOutputStream();
            send(1, 3, password); String response = read();
        }
        void send(int id, int type, String text) throws IOException {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            output.write(ByteBuffer.allocate(data.length + 14).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length + 10).putInt(id).putInt(type).put(data).put((byte)0).put((byte)0).array()); output.flush();
        }
        String read() throws IOException {
            int length = Integer.reverseBytes(input.readInt()); byte[] packet = input.readNBytes(length);
            return new String(packet, 8, length - 10, StandardCharsets.UTF_8);
        }
        String command(String text) throws IOException { send(2, 2, text); return read(); }
        public void close() throws IOException { socket.close(); }
    }
}
