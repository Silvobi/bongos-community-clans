package pl.bongo.clans;

import io.netty.buffer.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.nbt.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.*;
import net.minecraft.util.Crypt;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.*;
import javax.crypto.*;

/** Three unmodified vanilla-protocol clients; run against an isolated offline test server. */
public final class IntegrationHarness {
    static int port;
    static void check(boolean result,String label) { if(!result) throw new AssertionError(label); System.out.println("PASS: "+label); }
    static boolean hasColor(Component component, int rgb) {
        return component.visit((Style style, String text) -> style.getColor()!=null && style.getColor().getValue()==rgb
                && !text.isEmpty() ? Optional.of(true) : Optional.empty(),Style.EMPTY).orElse(false);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port=Integer.parseInt(args[0]);
        // This headless decoder only asserts count and explicit item patches, not vanilla base components.
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if (!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY); });
        if(args.length>1 && args[1].equals("restart")) { restart(args[2]); return; }
        String suffix=Long.toString(System.currentTimeMillis(),36);
        String tag="★🧈"+suffix.substring(suffix.length()-4)+"★";
        try(Bot owner=new Bot("CF"+suffix,"pl_pl"); Bot peer=new Bot("CM"+suffix,"en_us"); Bot outsider=new Bot("CO"+suffix,"en_us")) {
            owner.drain(100); peer.drain(100); outsider.drain(100);
            String clan="Clans"+suffix;
            owner.command("clan create "+clan+" &1"+tag); owner.message("Utworzono klan"); owner.drain(150);
            check(owner.teams.values().stream().anyMatch(p->p.playerPrefix().getString().equals("["+tag+"] ")),"Unicode team prefix is sent for vanilla nametags");
            check(owner.tabs.get(owner.uuid).getString().equals("["+tag+"] "+owner.name),"TAB gets the exact prefix and player name");
            check(hasColor(owner.tabs.get(owner.uuid),0x0000AA),"inline legacy color works when creating a clan");
            owner.command("clan change tag &#12ab34"+tag.substring(0,tag.length()-1)+"&4★"); owner.message("Tag klanu"); owner.drain(150);
            check(owner.teams.values().stream().anyMatch(p->hasColor(p.playerPrefix(),0x12AB34) && hasColor(p.playerPrefix(),0xAA0000)),"mixed HEX and legacy colors reach vanilla team packets");
            check(hasColor(owner.tabs.get(owner.uuid),0x12AB34) && hasColor(owner.tabs.get(owner.uuid),0xAA0000),"TAB receives both tag colors without raw codes");
            owner.command("clan change name &aBadName"); owner.message("Kody kolorów są niedozwolone");
            owner.command("clan invite "+peer.name); owner.message("Zaproszono"); peer.message("invites you");
            peer.command("clan accept"); peer.message("joined the clan"); owner.message("dołączył");
            peer.drain(100);
            check(peer.tabs.get(peer.uuid).getString().equals("["+tag+"] "+peer.name),"accepted member receives tag immediately");
            owner.command("clan chat PRIVATE_COMMAND_"+suffix); owner.message("PRIVATE_COMMAND_"); peer.message("PRIVATE_COMMAND_"); outsider.drain(100);
            check(outsider.messages.stream().noneMatch(m->m.getString().contains("PRIVATE_COMMAND_")),"explicit clan chat is hidden from nonmembers");
            owner.command("clan quickchat"); owner.message("Quickchat włączony");
            owner.chat("#PRIVATE_QUICK_"+suffix); owner.message("PRIVATE_QUICK_"); peer.message("PRIVATE_QUICK_"); outsider.drain(100);
            check(outsider.messages.stream().noneMatch(m->m.getString().contains("PRIVATE_QUICK_")),"#quickchat cannot leak to public chat");
            owner.chat("PUBLIC_"+suffix); Component publicChat=outsider.message("PUBLIC_");
            check(publicChat.getString().contains("["+tag+"] "+owner.name),"public chat includes the clan prefix");
            check(hasColor(publicChat,0x12AB34) && hasColor(publicChat,0xAA0000),"public chat keeps the mixed tag colors");
            peer.command("clan info"); var open=(ClientboundOpenScreenPacket)peer.await(p->p instanceof ClientboundOpenScreenPacket,5000);
            check(open.getType()==MenuType.GENERIC_9x6 && open.getTitle().getString().contains(clan),"info opens a vanilla chest GUI");
            var content=(ClientboundContainerSetContentPacket)peer.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==open.getContainerId(),5000);
            check(!content.items().get(1).isEmpty() && !content.items().get(18).isEmpty() && !content.items().get(19).isEmpty(),"GUI contains clan metadata and member heads");
            check(content.items().get(3).get(DataComponents.LORE).lines().stream().anyMatch(c->c.getString().contains("Founder:")),"GUI uses the viewer's English locale");
            var tagLabel=content.items().get(1).get(DataComponents.CUSTOM_NAME);
            check(tagLabel.getString().contains("Tag: ["+tag+"]") && hasColor(tagLabel,0x12AB34) && hasColor(tagLabel,0xAA0000),"GUI displays the colored tag without raw codes");
            for(ContainerInput input:List.of(ContainerInput.PICKUP,ContainerInput.QUICK_MOVE,ContainerInput.SWAP,ContainerInput.THROW,ContainerInput.CLONE,ContainerInput.QUICK_CRAFT,ContainerInput.PICKUP_ALL)) {
                peer.send(new ServerboundContainerClickPacket(open.getContainerId(),content.stateId(),(short)18,(byte)0,input,new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
                var unchanged=(ClientboundContainerSetContentPacket)peer.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==open.getContainerId(),5000);
                check(unchanged.carriedItem().isEmpty() && !unchanged.items().get(18).isEmpty(),"GUI blocks item extraction via "+input);
            }
            peer.send(new ServerboundContainerClosePacket(open.getContainerId()));
            owner.command("clan promote "+peer.name+" cofounder"); owner.message("Współzałożyciel"); peer.message("Cofounder");
            peer.command("clan change tag T"+suffix); peer.message("Clan tag changed"); owner.message("Tag klanu");
            owner.command("clan delete"); var waiting=(ClientboundShowDialogPacket)owner.await(p->p instanceof ClientboundShowDialogPacket,5000);
            var waitDialog=(MultiActionDialog)waiting.dialog().value();
            check(waitDialog.actions().getFirst().action().isEmpty(),"delete confirmation has no action during countdown");
            CustomAll cancel=(CustomAll)waitDialog.exitAction().orElseThrow().action().orElseThrow();
            var forged=Identifier.fromNamespaceAndPath(CommunityClans.ID,cancel.id().getPath().replace("cancel/","confirm/"));
            owner.send(new ServerboundCustomClickActionPacket(forged,Optional.empty())); owner.message("10-sekundowego");
            check(true,"forged early confirmation is rejected by the server");
            owner.send(new ServerboundCustomClickActionPacket(cancel.id(),Optional.empty())); owner.message("Anulowano");
            owner.command("clan info"); owner.await(p->p instanceof ClientboundOpenScreenPacket,5000);
            owner.command("clan delete"); owner.await(p->p instanceof ClientboundShowDialogPacket,5000);
            // Keep all clients responsive while the server unlocks confirmation.
            long deadline=System.currentTimeMillis()+15000; ActionButton confirmation=null;
            while(System.currentTimeMillis()<deadline && confirmation==null) {
                Object packet=owner.poll(100); peer.drain(5); outsider.drain(5);
                if(packet instanceof ClientboundShowDialogPacket shown) {
                    var dialog=(MultiActionDialog)shown.dialog().value();
                    if(dialog.actions().getFirst().action().isPresent()) confirmation=dialog.actions().getFirst();
                }
            }
            check(confirmation!=null,"confirmation unlocks after the real 10-second wait");
            CustomAll action=(CustomAll)confirmation.action().orElseThrow();
            owner.send(new ServerboundCustomClickActionPacket(action.id(),Optional.empty())); owner.message("został usunięty"); peer.message("was deleted");
            owner.drain(100); peer.drain(100);
            check(owner.tabs.get(owner.uuid)==null && peer.tabs.get(peer.uuid)==null,"deletion clears TAB tags for all members");
            owner.send(new ServerboundCustomClickActionPacket(action.id(),Optional.empty())); owner.message("Potwierdzenie wygasło");
            check(true,"used confirmation tokens cannot be replayed");
            peer.command("clan ignore"); peer.message("Ignoring invitations enabled");
            owner.command("clan create "+clan+" &aN"+suffix); owner.message("Utworzono klan");
            owner.command("clan invite "+peer.name); owner.message("ignoruje zaproszenia");
            owner.command("clan invite "+outsider.name); owner.message("Zaproszono"); outsider.message("invites you");
            outsider.command("clan accept"); outsider.message("joined the clan");
            outsider.command("clan leave"); outsider.message("You left clan");
            check(true,"ignore preference and leave are usable without operator permissions");
            owner.command("clan quickchat"); owner.message("Quickchat włączony");
            // Leave one clan on disk for a restart smoke check.
        }
        System.out.println("PASS: all vanilla-protocol integration scenarios completed.");
    }
    static void restart(String name) throws Exception {
        try(Bot owner=new Bot(name,"pl_pl")) {
            owner.drain(300);
            Component tab=owner.tabs.get(owner.uuid);
            check(tab!=null && tab.getString().startsWith("[N"),"clan membership and TAB tag survive a real server restart");
            check(hasColor(tab,0x55FF55),"inline tag color survives a real server restart");
            check(owner.teams.values().stream().anyMatch(p->p.playerPrefix().getString().startsWith("[N")),"nametag team is restored after restart");
            owner.chat("#RESTART_PRIVATE"); owner.message("[KLAN]");
            check(true,"quickchat preference survives restart");
            owner.command("clan info"); var opened=(ClientboundOpenScreenPacket)owner.await(p->p instanceof ClientboundOpenScreenPacket,5000);
            var page1=(ClientboundContainerSetContentPacket)owner.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==opened.getContainerId(),5000);
            check(page1.items().get(49).get(DataComponents.LORE).lines().getFirst().getString().equals("Strona 1 / 2"),"large clan is paginated in inventory GUI");
            owner.send(new ServerboundContainerClickPacket(opened.getContainerId(),page1.stateId(),(short)50,(byte)0,ContainerInput.PICKUP,new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
            var next=(ClientboundOpenScreenPacket)owner.await(p->p instanceof ClientboundOpenScreenPacket s && s.getContainerId()!=opened.getContainerId(),5000);
            var page2=(ClientboundContainerSetContentPacket)owner.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==next.getContainerId(),5000);
            check(page2.items().get(49).get(DataComponents.LORE).lines().getFirst().getString().equals("Strona 2 / 2") && !page2.items().get(18).isEmpty(),"next page contains remaining members");
            owner.send(new ServerboundContainerClosePacket(next.getContainerId()));
            owner.command("clan change tag &aRestart"); owner.message("Tag klanu"); owner.drain(100);
            check(owner.teams.values().stream().anyMatch(p->hasColor(p.playerPrefix(),0x55FF55)),"inline &a color code works unquoted after restart");
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    static final class Bot implements AutoCloseable {
        final String name; UUID uuid;
        final Socket socket;
        InputStream input; OutputStream output;
        StreamCodec inbound=LoginProtocols.CLIENTBOUND.codec(),outbound=HandshakeProtocols.SERVERBOUND.codec();
        int compression=-1;
        RegistryAccess access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final List<Component> messages=new ArrayList<>();
        final Map<UUID,Component> tabs=new HashMap<>();
        final Map<String,ClientboundSetPlayerTeamPacket.Parameters> teams=new HashMap<>();
        final List<Object> events=new ArrayList<>();
        final List<ClientboundResourcePackPushPacket> configurationPacks=new ArrayList<>();
        final Map<Integer,net.minecraft.network.protocol.PacketType<?>> types=new HashMap<>();
        @FunctionalInterface interface PackReply {
            void handle(Bot bot,ClientboundResourcePackPushPacket pack) throws Exception;
        }
        Bot(String name,String language) throws Exception {
            this(name,language,(bot,pack)-> {
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.ACCEPTED));
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DOWNLOADED));
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            });
        }
        Bot(String name,String language,PackReply packReply) throws Exception {
            this.name=name; uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            socket=new Socket("127.0.0.1",port); socket.setSoTimeout(20000); input=socket.getInputStream();output=socket.getOutputStream();
            send(new ClientIntentionPacket(SharedConstants.getProtocolVersion(),"localhost",port,ClientIntent.LOGIN));
            outbound=LoginProtocols.SERVERBOUND.codec();send(new ServerboundHelloPacket(name,uuid));
            while(true) {
                Packet<?> packet=read();
                if(packet instanceof ClientboundHelloPacket hello) {
                    var secret=Crypt.generateSecretKey();send(new ServerboundKeyPacket(secret,hello.getPublicKey(),hello.getChallenge()));
                    input=new CipherInputStream(input,Crypt.getCipher(2,secret));output=new CipherOutputStream(output,Crypt.getCipher(1,secret));
                } else if(packet instanceof ClientboundLoginCompressionPacket threshold) compression=threshold.getCompressionThreshold();
                else if(packet instanceof ClientboundLoginDisconnectPacket kick) throw new AssertionError(kick.reason());
                else if(packet instanceof ClientboundLoginFinishedPacket finished) { uuid=finished.gameProfile().id(); break; }
            }
            send(ServerboundLoginAcknowledgedPacket.INSTANCE);inbound=ConfigurationProtocols.CLIENTBOUND.codec();outbound=ConfigurationProtocols.SERVERBOUND.codec();
            var options=new ClientInformation(language,2,ChatVisiblity.FULL,true,127,HumanoidArm.RIGHT,false,true,ParticleStatus.ALL);
            send(new ServerboundClientInformationPacket(options));
            while(true) {
                Packet<?> packet=read();
                if(packet instanceof ClientboundSelectKnownPacks) send(new ServerboundSelectKnownPacks(List.of()));
                else if(packet instanceof ClientboundResourcePackPushPacket pack) {
                    configurationPacks.add(pack);
                    packReply.handle(this,pack);
                }
                else if(packet instanceof ClientboundShowDialogPacket shown) {
                    MultiActionDialog dialog=(MultiActionDialog)shown.dialog().value();
                    CompoundTag fields=new CompoundTag();fields.putString("password","clans-test-password");fields.putString("repeat","clans-test-password");
                    CustomAll action=(CustomAll)dialog.actions().getFirst().action().orElseThrow();
                    send(new ServerboundCustomClickActionPacket(action.id(),Optional.of(fields)));
                } else if(packet instanceof ClientboundRegistryDataPacket data && data.registry().equals(Registries.CHAT_TYPE)) {
                    var chatTypes=new MappedRegistry<ChatType>(Registries.CHAT_TYPE,Lifecycle.stable());
                    for(var entry:data.entries()) chatTypes.register(ResourceKey.create(Registries.CHAT_TYPE,entry.id()),ChatType.DIRECT_CODEC.parse(NbtOps.INSTANCE,entry.data().orElseThrow()).getOrThrow(),RegistrationInfo.BUILT_IN);
                    chatTypes.freeze();
                    List<Registry<?>> registries=new ArrayList<>(access.registries().map(RegistryAccess.RegistryEntry::value).toList());registries.add(chatTypes);
                    access=new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
                } else if(packet instanceof ClientboundKeepAlivePacket alive) send(new ServerboundKeepAlivePacket(alive.getId()));
                else if(packet instanceof ClientboundPingPacket ping) send(new ServerboundPongPacket(ping.getId()));
                else if(packet instanceof ClientboundDisconnectPacket kick) throw new AssertionError(kick.reason());
                else if(packet instanceof ClientboundFinishConfigurationPacket) break;
            }
            send(ServerboundFinishConfigurationPacket.INSTANCE);
            outbound=GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access),new GameProtocols.Context() {
                public boolean hasInfiniteMaterials(){return false;}public boolean canUseCommandBlocks(){return false;}
            }).codec();
            GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,index)->types.put(index,type));
            send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            send(new ServerboundPlayerLoadedPacket());
            drain(150);
        }
        String teamPrefix() { return teams.values().stream().filter(p->p.playerPrefix().getString().startsWith("[★")).findFirst().orElseThrow().playerPrefix().getString(); }
        void command(String command) throws Exception { send(new ServerboundChatCommandPacket(command)); }
        void chat(String message) throws Exception { send(new ServerboundChatPacket(message,Instant.now(),0,Optional.empty(),new LastSeenMessages.Update(0,new BitSet(),LastSeenMessages.Update.IGNORE_CHECKSUM))); }
        Component message(String contains) throws Exception {
            Object p=await(e->e instanceof Component c && c.getString().contains(contains),5000);return (Component)p;
        }
        Object await(Predicate<Object> predicate,long timeout) throws Exception {
            long deadline=System.currentTimeMillis()+timeout;
            while(System.currentTimeMillis()<deadline) {
                for(int i=0;i<events.size();i++) if(predicate.test(events.get(i))) return events.remove(i);
                poll(100);
            }
            throw new AssertionError(name+" timed out; recent chat="+messages.stream().map(Component::getString).toList());
        }
        void drain(long millis) throws Exception { long until=System.currentTimeMillis()+millis;do{poll(5);}while(System.currentTimeMillis()<until); }
        Object poll(long timeout) throws Exception {
            long deadline=System.currentTimeMillis()+timeout;
            while(input.available()==0 && socket.getInputStream().available()==0) {if(System.currentTimeMillis()>=deadline)return null;Thread.sleep(2);}
            var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(frame()),access);
            Object result=null;
            try {
                var type=types.get(buffer.readVarInt());
                if(type==CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE) send(new ServerboundKeepAlivePacket(ClientboundKeepAlivePacket.STREAM_CODEC.decode(buffer).getId()));
                else if(type==CommonPacketTypes.CLIENTBOUND_PING) send(new ServerboundPongPacket(ClientboundPingPacket.STREAM_CODEC.decode(buffer).getId()));
                else if(type==CommonPacketTypes.CLIENTBOUND_DISCONNECT) throw new AssertionError(ClientboundDisconnectPacket.STREAM_CODEC.decode(buffer).reason());
                else if(type==CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG) result=ClientboundShowDialogPacket.STREAM_CODEC.decode(buffer);
                else if(type==CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_PUSH) result=ClientboundResourcePackPushPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_ADD_ENTITY) result=ClientboundAddEntityPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_BLOCK_UPDATE) result=ClientboundBlockUpdatePacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA) result=ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES) result=ClientboundRemoveEntitiesPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SOUND) result=ClientboundSoundPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_STOP_SOUND) result=ClientboundStopSoundPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_POSITION) {
                    var p=ClientboundPlayerPositionPacket.STREAM_CODEC.decode(buffer);var c=p.change();send(new ServerboundAcceptTeleportationPacket(p.id(),c.position().x,c.position().y,c.position().z,c.yRot(),c.xRot()));
                } else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE) {
                    var p=ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
                    if(p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) for(var e:p.entries()) tabs.put(e.profileId(),e.displayName());
                    result=p;
                } else if(type==GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM) {
                    var p=ClientboundSetPlayerTeamPacket.STREAM_CODEC.decode(buffer);p.getParameters().ifPresent(params->teams.put(p.getName(),params));
                    if(p.getTeamAction()==ClientboundSetPlayerTeamPacket.Action.REMOVE) teams.remove(p.getName());result=p;
                } else if(type==GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT) result=ClientboundSystemChatPacket.STREAM_CODEC.decode(buffer).content();
                else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_CHAT) {
                    var p=ClientboundPlayerChatPacket.STREAM_CODEC.decode(buffer);result=p.chatType().decorate(p.unsignedContent().orElseGet(()->Component.literal(p.body().content())));
                } else if(type==GamePacketTypes.CLIENTBOUND_DISGUISED_CHAT) {
                    var p=ClientboundDisguisedChatPacket.STREAM_CODEC.decode(buffer);result=p.chatType().decorate(p.message());
                } else if(type==GamePacketTypes.CLIENTBOUND_OPEN_SCREEN) result=ClientboundOpenScreenPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_COMMAND_SUGGESTIONS) result=ClientboundCommandSuggestionsPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT) result=ClientboundContainerSetContentPacket.STREAM_CODEC.decode(buffer);
            } finally {buffer.release();}
            if(result!=null){events.add(result);if(result instanceof Component c)messages.add(c);}
            return result;
        }
        Packet<?> read() throws Exception {ByteBuf b=Unpooled.wrappedBuffer(frame());try{return(Packet<?>)inbound.decode(b);}finally{b.release();}}
        byte[] frame() throws Exception {
            int size=varInt(input);if(size<0||size>8388608)throw new IOException("Bad frame size");byte[] bytes=new byte[size];
            for(int offset=0;offset<size;){int count=input.read(bytes,offset,size-offset);if(count<0)throw new EOFException();offset+=count;}
            if(compression>=0){var compressed=new ByteArrayInputStream(bytes);int original=varInt(compressed);bytes=original==0?compressed.readAllBytes():new InflaterInputStream(compressed).readAllBytes();}return bytes;
        }
        void send(Packet<?> packet) throws Exception {
            ByteBuf buffer=Unpooled.buffer();byte[] bytes;
            try{outbound.encode(buffer,packet);bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);}finally{buffer.release();}
            if(compression>=0){var frame=new ByteArrayOutputStream();if(bytes.length>=compression){varInt(frame,bytes.length);var deflate=new DeflaterOutputStream(frame);deflate.write(bytes);deflate.finish();}else{varInt(frame,0);frame.write(bytes);}bytes=frame.toByteArray();}
            varInt(output,bytes.length);output.write(bytes);output.flush();
        }
        public void close() throws IOException {socket.close();}
    }
    static int varInt(InputStream input)throws IOException {int value=0;for(int i=0;i<5;i++){int b=input.read();if(b<0)throw new EOFException();value|=(b&127)<<(i*7);if((b&128)==0)return value;}throw new IOException("Bad VarInt");}
    static void varInt(OutputStream output,int value)throws IOException {do{int b=value&127;value>>>=7;output.write(b|(value==0?0:128));}while(value!=0);}
}
