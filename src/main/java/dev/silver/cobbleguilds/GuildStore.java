package dev.silver.cobbleguilds;

import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** World-local stores. Disbanded guilds have separate immutable membership history and cannot be restored. */
public final class GuildStore {
    private final Path root;
    private final Map<String,Guild> guilds = new LinkedHashMap<>();
    private final Map<String,Guild> archives = new LinkedHashMap<>();
    public GuildStore(MinecraftServer server) {
        root = server.getSavePath(WorldSavePath.ROOT).resolve("cobbleguilds");
        try {
            Files.createDirectories(root);
            read(root.resolve("guilds.json"),guilds);
            read(root.resolve("disbanded-guilds.json"),archives);
            // One-time migration from older releases: write the archive FIRST.
            boolean migrate=false;
            for (Guild old: List.copyOf(guilds.values())) if(old.closed) {
                archives.putIfAbsent(old.id,old);guilds.remove(old.id);migrate=true;
            }
            for(Guild g:archives.values())g.closed=true;
            for(Guild g:guilds.values())if(g.memberNames==null)g.memberNames=new LinkedHashMap<>();
            for(Guild g:archives.values())if(g.memberNames==null)g.memberNames=new LinkedHashMap<>();
            if(migrate){saveArchives();saveActive();}
        } catch(Exception e){throw new IllegalStateException("Unable to load guild data - refusing to start to prevent data loss",e);}
    }
    private void read(Path file,Map<String,Guild> target) throws java.io.IOException {
        if(!Files.exists(file))return;
        Map<String,Guild> data=GuildConfig.JSON.fromJson(Files.readString(file),new TypeToken<Map<String,Guild>>(){}.getType());
        if(data==null)throw new IllegalStateException("Invalid data in "+file);
        target.putAll(data);
    }
    public Path root(){return root;}
    public Collection<Guild> all(){return Collections.unmodifiableCollection(guilds.values());}
    public Collection<Guild> disbanded(){return Collections.unmodifiableCollection(archives.values());}
    public Guild archived(String id){return archives.get(id);}
    public Guild named(String name){return guilds.values().stream().filter(g->!g.closed&&(g.name.equalsIgnoreCase(name)||g.tag.equalsIgnoreCase(name))).findFirst().orElse(null);}
    public Guild byId(String id){return guilds.get(id);}
    public Guild member(UUID id){return guilds.values().stream().filter(g->g.contains(id)).findFirst().orElse(null);}
    public void put(Guild g){if(g.closed)throw new IllegalArgumentException("Cannot restore a disbanded guild");guilds.put(g.id,g);saveActive();}
    public void disband(Guild g,String why){
        if(g==null||!guilds.containsKey(g.id)||g.closed)return;
        g.closed=true;g.addAudit(why);
        archives.put(g.id,g);
        // Crash-safe ordering: archive is durable before active entry is removed.
        saveArchives();guilds.remove(g.id);saveActive();
    }
    public void deleteDisbanded(String id){
        if(!archives.containsKey(id))return;
        Guild old=archives.remove(id);
        try{saveArchives();Files.deleteIfExists(chest(id));}
        catch(Exception e){archives.put(id,old);saveArchives();throw new IllegalStateException("Unable to delete archived guild "+id,e);}
    }
    public int deleteAllDisbanded(){
        int count=archives.size();
        for(String id:List.copyOf(archives.keySet()))deleteDisbanded(id);
        return count;
    }
    public Path chest(String id){return root.resolve("chest-"+id+".nbt");}
    public void save(){saveArchives();saveActive();}
    private void saveActive(){atomic(root.resolve("guilds.json"),guilds);}
    private void saveArchives(){atomic(root.resolve("disbanded-guilds.json"),archives);}
    private void atomic(Path path,Object state){
        try {
            Files.createDirectories(root);
            Path temp=path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(temp,GuildConfig.JSON.toJson(state),StandardCharsets.UTF_8);
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){throw new IllegalStateException("Unable to save guild data at "+path,e);}
    }
}
