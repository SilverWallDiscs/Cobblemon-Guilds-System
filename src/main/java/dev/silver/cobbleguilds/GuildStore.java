package dev.silver.cobbleguilds;

import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Atomic per-world JSON snapshots. Kept on the main server thread. */
public final class GuildStore {
    private final Path root;
    private final Map<String,Guild> guilds = new LinkedHashMap<>();
    public GuildStore(MinecraftServer server) {
        root = server.getSavePath(WorldSavePath.ROOT).resolve("cobbleguilds");
        try {
            Files.createDirectories(root);
            Path data = root.resolve("guilds.json");
            if(Files.exists(data)) {
                Map<String,Guild> loaded=GuildConfig.JSON.fromJson(Files.readString(data),
                    new TypeToken<Map<String,Guild>>(){}.getType());
                if(loaded!=null) guilds.putAll(loaded);
            }
        } catch(Exception e){ throw new IllegalStateException("Unable to load guild data - not starting to prevent data loss",e); }
    }
    public Path root(){return root;}
    public Collection<Guild> all(){return Collections.unmodifiableCollection(guilds.values());}
    public Guild named(String name){return guilds.values().stream().filter(g->!g.closed&&(g.name.equalsIgnoreCase(name)||g.tag.equalsIgnoreCase(name))).findFirst().orElse(null);}
    public Guild byId(String id){return guilds.get(id);}
    public Guild member(UUID id){return guilds.values().stream().filter(g->g.contains(id)).findFirst().orElse(null);}
    public void put(Guild g){guilds.put(g.id,g);save();}
    public void save(){
        try {
            Files.createDirectories(root);
            Path temp=root.resolve("guilds.json.tmp");
            Files.writeString(temp,GuildConfig.JSON.toJson(guilds),StandardCharsets.UTF_8);
            try { Files.move(temp,root.resolve("guilds.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,root.resolve("guilds.json"),StandardCopyOption.REPLACE_EXISTING);}
        } catch(Exception e){throw new IllegalStateException("Unable to save guild data",e);}
    }
}
