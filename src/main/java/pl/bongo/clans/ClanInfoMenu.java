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
    private final ClanBrowserMenu.Position back;
    private static final long QUICK_CLOSE_NANOS = 500_000_000L;
    private boolean shiftPending;
    private long lastShift;
    public static void open(ClanRuntime runtime, ServerPlayer player, int page) {
        Clan clan = runtime.service.require(player.getUUID());
        open(runtime, player, clan.id, page, null);
    }
    public static void open(ClanRuntime runtime, ServerPlayer player, UUID clanId, int page, ClanBrowserMenu.Position back) {
        Clan clan = runtime.service.state().clans.get(clanId);
        if (clan == null) throw new ClanException("clan_not_found");
        int pages = ClanMenus.pages(clan.members.size()), selected = Math.clamp(page,0,pages-1);
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ClanInfoMenu(id,inventory,runtime,clan,player,selected,pages,back),
                Lang.component(player,"info_title",clan.name)));
    }
    private ClanInfoMenu(int id, Inventory inventory, ClanRuntime runtime, Clan clan, ServerPlayer viewer, int page, int pages, ClanBrowserMenu.Position back) {
        super(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),6);
        this.runtime=runtime; this.clanId=clan.id; this.page=page; this.pages=pages;
        this.back=back;
        var container=getContainer();
        ClanMenus.fill(container);
        String date=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(clan.createdAt));
        container.setItem(1,item(Items.NAME_TAG,Lang.text(viewer,"info_name",clan.name),List.of()));
        container.getItem(1).set(DataComponents.CUSTOM_NAME,Lang.component(viewer,"info_tag",CommunityClans.bracketedTag(clan)));
        container.setItem(3,item(Items.CLOCK,Lang.text(viewer,"info_date",date),List.of(Lang.text(viewer,"info_founder",clan.founder().name))));
        container.setItem(5,item(ClanMenus.sword(clan.members.size()),Lang.text(viewer,"info_members",clan.members.size()),List.of()));
        if(pl.bongo.clans.claims.ClanClaims.gp!=null) {
            var stats=pl.bongo.clans.claims.ClaimLeaderboard.current(runtime).get(clan.id);
            var lore=new ArrayList<String>();
            lore.add(Lang.text(viewer,"info_area",stats.area()));
            for (String world : List.of("world","world_nether","world_the_end"))
                lore.add("  "+worldLabel(world)+": "+stats.worlds().getOrDefault(world,0L)+" m²");
            stats.worlds().entrySet().stream().filter(e -> !List.of("world","world_nether","world_the_end").contains(e.getKey()))
                    .sorted(Map.Entry.comparingByKey()).forEach(e -> lore.add("  "+worldLabel(e.getKey())+": "+e.getValue()+" m²"));
            container.setItem(7,item(Items.SPAWNER,Lang.text(viewer,"info_claims",stats.claims()),lore));
        }
        List<Member> members=clan.members.values().stream().sorted(Comparator.<Member>comparingInt(m -> -m.role.power)
                .thenComparing(m -> m.name.toLowerCase(Locale.ROOT))).toList();
        for (int i=page*ClanMenus.PAGE_SIZE;i<Math.min(members.size(),(page+1)*ClanMenus.PAGE_SIZE);i++) {
            Member member=members.get(i); ServerPlayer online=runtime.server.getPlayerList().getPlayer(member.id);
            ItemStack head=item(Items.PLAYER_HEAD,member.name,List.of(
                    Lang.text(viewer,"info_rank",Lang.text(viewer,"role_"+member.role.name().toLowerCase(Locale.ROOT))),
                    Lang.text(viewer,online==null ? "info_offline" : "info_online")));
            if (online!=null) head.set(DataComponents.PROFILE,ResolvableProfile.createResolved(online.getGameProfile()));
            container.setItem(18+i-page*ClanMenus.PAGE_SIZE,head);
        }
        if (back!=null) container.setItem(45,item(Items.ARROW,Lang.text(viewer,"info_back"),List.of()));
        if (page>0) container.setItem(48,item(Items.PAPER,Lang.text(viewer,"info_previous"),List.of()));
        container.setItem(49,item(Items.BARRIER,Lang.text(viewer,"info_close"),List.of(Lang.text(viewer,"info_page",page+1,pages))));
        if (page+1<pages) container.setItem(50,item(Items.PAPER,Lang.text(viewer,"info_next"),List.of()));
    }
    private static String worldLabel(String key) {
        return switch (key) { case "world" -> "world"; case "world_nether" -> "nether"; case "world_the_end" -> "the end"; default -> key; };
    }
    private static ItemStack item(Item type,String name,List<String> lore) {
        return ClanMenus.item(type,name,lore);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return runtime.service.state().clans.containsKey(clanId);
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
            if (slot==49) { viewer.closeContainer(); return; }
            if (slot==45 && back!=null) { ClanBrowserMenu.open(runtime,viewer,back); return; }
            if (slot==48 && page>0) { open(runtime,viewer,clanId,page-1,back); return; }
            if (slot==50 && page+1<pages) { open(runtime,viewer,clanId,page+1,back); return; }
        }
        broadcastFullState();
    }
}
