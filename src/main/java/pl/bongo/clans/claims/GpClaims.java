package pl.bongo.clans.claims;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/** Reflection boundary for the supported GP3D 19 universal Fabric adapter. No bundled GP classes. */
public final class GpClaims {
    public final Object repository;
    public GpClaims(Object repository) { this.repository=Objects.requireNonNull(repository); }
    public static GpClaims detect() {
        if(!FabricLoader.getInstance().isModLoaded("griefprevention3d")) return null;
        try {
            Class<?> entry=Class.forName("com.griefprevention.fabric.mc26_1.GriefPreventionFabric");
            return new GpClaims(entry.getMethod("getClaimRepository").invoke(null));
        } catch(ReflectiveOperationException | LinkageError e) { throw new IllegalStateException("Nieobsługiwana wersja GriefPrevention3D (wymagana 19.0.0 universal).",e); }
    }
    public static Object call(Object target,String name,Object... args) {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) for(Method method:type.getDeclaredMethods()) {
            if(!method.getName().equals(name) || method.getParameterCount()!=args.length) continue;
            Class<?>[] params=method.getParameterTypes(); boolean match=true;
            for(int i=0;i<args.length;i++) {
                Class<?> p=params[i];
                if(p==long.class) p=Long.class; else if(p==int.class) p=Integer.class; else if(p==boolean.class) p=Boolean.class;
                if(args[i]!=null && !p.isInstance(args[i])) { match=false; break; }
            }
            if(!match) continue;
            try { method.setAccessible(true); return method.invoke(target,args); }
            catch(ReflectiveOperationException | RuntimeException e) { throw new IllegalStateException("GP3D: błąd operacji "+name,e); }
        }
        throw new IllegalStateException("GP3D: brak metody "+name);
    }
    public static Object field(Object target,String name) {
        try { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("GP3D: brak pola "+name,e); }
    }
    @SuppressWarnings("unchecked") public Collection<Object> all() { return (Collection<Object>)call(repository,"getClaims"); }
    public Object byId(long id) { return ((Optional<?>)call(repository,"getClaim",id)).orElse(null); }
    public UUID owner(Object claim) { return (UUID)call(repository,"effectiveOwnerId",claim); }
}
