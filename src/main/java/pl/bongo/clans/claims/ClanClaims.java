package pl.bongo.clans.claims;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import pl.bongo.clans.*;

/** Additive, live clan trust. It never edits native trust lists or grants management permissions. */
public final class ClanClaims {
    public static final String FLAG="bongo_community_clans:access";
    public static GpClaims gp;
    private static Class<?> shared;
    private static boolean loggedFailure;
    private static ClanData.State indexedState;
    private static Map<UUID,UUID> membership=Map.of();
    public static void register() {
        if(!FabricLoader.getInstance().isModLoaded("bongoutils")) return;
        try {
            shared=Class.forName("pl.bongo.bongoutils.claims.ClaimFlags");
            shared.getMethod("register",String.class,String.class,String.class,List.class,String.class,boolean.class)
                .invoke(null,FLAG,"Dostęp członków klanu","Clan member access",List.of("off","access","container","build"),"build",false);
        } catch(ClassNotFoundException e) { shared=null; }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("Nie można zarejestrować flagi klanowej.",e); }
    }
    public static void start() { gp=GpClaims.detect(); }
    public static void stop() { gp=null;indexedState=null;membership=Map.of();loggedFailure=false; }
    public static Map<UUID,UUID> membership(ClanData.State state) {
        if(indexedState!=state) {
            Map<UUID,UUID> next=new HashMap<>();
            for(var clan:state.clans.values()) for(UUID player:clan.members.keySet()) next.put(player,clan.id);
            membership=Map.copyOf(next);indexedState=state;
        }
        return membership;
    }
    private static Object shared(String method,Class<?>[] signature,Object...args) {
        try { return shared.getMethod(method,signature).invoke(null,args); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("Błąd wspólnych flag działek.",e); }
    }
    public static boolean levelAllows(String sharing,String required) {
        int available=switch(sharing) { case "build"->3;case "container"->2;case "access"->1;case "off"->0;default->throw new IllegalArgumentException("Invalid clan access level"); };
        int need=switch(required) { case "BUILD"->3;case "CONTAINER"->2;case "ACCESS"->1;default->4; };
        return available>=need;
    }
    public static boolean extraAccess(Object repository,Object claim,UUID player,Object required) {
        String trust=((Enum<?>)required).name();
        if(!Set.of("BUILD","CONTAINER","ACCESS").contains(trust) || CommunityClans.runtime==null) return false;
        try {
            GpClaims api=gp;if(api==null || api.repository!=repository) api=new GpClaims(repository);
            UUID owner=api.owner(claim);if(owner==null || (boolean)GpClaims.call(repository,"isAdminSubdivision",claim)) return false;
            var members=membership(CommunityClans.runtime.service.state());
            UUID clan=members.get(owner);
            if(clan==null || !clan.equals(members.get(player))) return false;
            Object parentId=GpClaims.call(claim,"parentId");
            boolean independent=parentId!=null && ((boolean)GpClaims.call(repository,"isRestrictedSubdivision",claim)
                || GpClaims.call(api.byId((Long)parentId),"parentId")!=null);
            boolean hasLocal=shared!=null && (boolean)shared("hasLocal",new Class<?>[]{Object.class,String.class},claim,FLAG);
            if(independent && !hasLocal) return false;
            String sharing=shared==null?"build":(String)shared("value",new Class<?>[]{Object.class,String.class,String.class},claim,FLAG,"build");
            if(!levelAllows(sharing,trust)) return false;
            // The extra grant must not override a native UUID/permission/public denial.
            Object cursor=claim;
            while(cursor!=null) {
                Object snapshot=GpClaims.call(repository,"trustForOrEmpty",cursor);
                Object permissions=GpClaims.field(repository,"permissions");
                Class<?> evaluator=Class.forName(repository.getClass().getPackageName()+".FabricClaimTrustEvaluator");
                Method subject=Arrays.stream(evaluator.getDeclaredMethods()).filter(m->m.getName().equals("subject")).findFirst().orElseThrow();
                subject.setAccessible(true);Object actor=subject.invoke(null,player,snapshot,permissions);
                if((boolean)GpClaims.call(snapshot,"isPermissionDenied",actor,required)) return false;
                Long parent=(Long)GpClaims.call(cursor,"parentId");cursor=parent==null?null:api.byId(parent);
            }
            return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
            if(!loggedFailure) { CommunityClans.LOGGER.error("Could not evaluate clan claim trust; extra access denied",e);loggedFailure=true; }
            return false;
        }
    }
}
