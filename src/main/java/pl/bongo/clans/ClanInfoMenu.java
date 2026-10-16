package pl.bongo.clans;

import static pl.bongo.clans.ClanData.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Vanilla chest protocol, server-owned decorative items, no movement or extraction. */
public final class ClanInfoMenu extends ChestMenu {
    private final ClanRuntime runtime;
    private final UUID clanId;
    private final int page, pages;
    private static final long QUICK_CLOSE_NANOS = 500_000_000L;
    private boolean shiftPending;
    private long lastShift;
    public static void open(ClanRuntime runtime, ServerPlayer player, int page) {
        Clan clan = runtime.service.require(player.getUUID());
        int pages = Math.max(1,(clan.members.size()+35)/36), selected = Math.min(Math.max(0,page),pages-1);
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ClanInfoMenu(id,inventory,runtime,clan,player,selected,pages),
                Lang.component(player,"info_title",clan.name)));
    }
    private ClanInfoMenu(int id, Inventory inventory, ClanRuntime runtime, Clan clan, ServerPlayer viewer, int page, int pages) {
        super(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),6);
        this.runtime=runtime; this.clanId=clan.id; this.page=page; this.pages=pages;
        var container=getContainer();
        String date=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(clan.createdAt));
        container.setItem(4,item(Items.NAME_TAG,Lang.text(viewer,"info_name",clan.name),List.of()));
        container.getItem(4).set(DataComponents.CUSTOM_NAME,CommunityClans.prefix(clan));
        container.getItem(4).set(DataComponents.LORE,new ItemLore(List.of(
                Lang.component(viewer,"info_name",clan.name),Lang.component(viewer,"info_tag",CommunityClans.bracketedTag(clan)),
                Lang.component(viewer,"info_date",date),Lang.component(viewer,"info_founder",clan.founder().name),
                Lang.component(viewer,"info_members",clan.members.size())).stream()
                .<Component>map(c -> c.copy().withStyle(ChatFormatting.GRAY)).toList()));
        List<Member> members=clan.members.values().stream().sorted(Comparator.<Member>comparingInt(m -> -m.role.power)
                .thenComparing(m -> m.name.toLowerCase(Locale.ROOT))).toList();
        for (int i=page*36;i<Math.min(members.size(),(page+1)*36);i++) {
            Member member=members.get(i); ServerPlayer online=runtime.server.getPlayerList().getPlayer(member.id);
            ItemStack head=item(Items.PLAYER_HEAD,member.name,List.of(
                    Lang.text(viewer,"info_rank",Lang.text(viewer,"role_"+member.role.name().toLowerCase(Locale.ROOT))),
                    Lang.text(viewer,online==null ? "info_offline" : "info_online")));
            if (online!=null) head.set(DataComponents.PROFILE,ResolvableProfile.createResolved(online.getGameProfile()));
            container.setItem(9+i-page*36,head);
        }
        if (page>0) container.setItem(45,item(Items.ARROW,Lang.text(viewer,"info_previous"),List.of()));
        container.setItem(49,item(Items.PAPER,Lang.text(viewer,"info_page",page+1,pages),List.of()));
        container.setItem(48,item(Items.BARRIER,Lang.text(viewer,"info_close"),List.of()));
        if (page+1<pages) container.setItem(53,item(Items.ARROW,Lang.text(viewer,"info_next"),List.of()));
    }
    private static ItemStack item(Item type,String name,List<String> lore) {
        ItemStack stack=new ItemStack(type);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(name).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false)));
        stack.set(DataComponents.LORE,new ItemLore(lore.stream().<Component>map(s -> Component.literal(s).withStyle(ChatFormatting.GRAY)).toList()));
        return stack;
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        Clan c=runtime.service.state().clan(player.getUUID()); return c!=null && c.id.equals(clanId);
    }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) { return false; }
    /** Called only after a matching close packet has closed this exact GUI. */
    public void clientClosed(ServerPlayer viewer) {
        boolean pending = shiftPending; shiftPending = false;
        long elapsed = System.nanoTime() - lastShift;
        if (pending && elapsed >= 0 && elapsed <= QUICK_CLOSE_NANOS && stillValid(viewer)) runtime.cat.prank(viewer);
    }
    @Override public void clicked(int slot,int button,ContainerInput input,Player player) {
        // Block every input (shift, hotbar swaps, drops, creative cloning, drag and double clicks).
        if (!(player instanceof ServerPlayer viewer)) return;
        shiftPending = input == ContainerInput.QUICK_MOVE && slot >= 0 && slot < slots.size()
                && slots.get(slot).hasItem() && stillValid(viewer) && !viewer.hasInfiniteMaterials() && !viewer.isSpectator();
        if (shiftPending) lastShift = System.nanoTime();
        if (input==ContainerInput.PICKUP && button==0 && stillValid(player)) {
            if (slot==48) { viewer.closeContainer(); return; }
            if (slot==45 && page>0) { open(runtime,viewer,page-1); return; }
            if (slot==53 && page+1<pages) { open(runtime,viewer,page+1); return; }
        }
        broadcastFullState();
    }
}
