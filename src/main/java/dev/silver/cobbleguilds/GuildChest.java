package dev.silver.cobbleguilds;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Persistent vanilla inventory, shared by viewers and saved synchronously on every change. */
public final class GuildChest {
    private final MinecraftServer server;
    private final GuildStore store;
    private final Map<String, ChestInventory> loaded=new HashMap<>();
    public GuildChest(MinecraftServer server,GuildStore store){this.server=server;this.store=store;}
    public SimpleInventory inventory(Guild g){
        return loaded.computeIfAbsent(g.id,id->{
            ChestInventory inv=new ChestInventory(g.id);
            Path f=file(id);
            if(Files.exists(f)){
                try {
                    NbtCompound root=NbtIo.readCompressed(f,NbtSizeTracker.ofUnlimitedBytes());
                    NbtList entries=root.getList("Items",10);
                    inv.loading=true;
                    for(int j=0;j<entries.size();j++){
                        NbtCompound entry=entries.getCompound(j);
                        int slot=entry.getInt("Slot");
                        if(slot<0||slot>=54)continue;
                        ItemStack stack=ItemStack.fromNbt(server.getRegistryManager(),entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                        inv.setStack(slot,stack);
                    }
                }catch(Exception e){throw new IllegalStateException("Could not load guild chest "+id+". Inventory locked to prevent data loss.",e);}
                finally {inv.loading=false;}
            }
            return inv;
        });
    }
    private Path file(String id){return store.root().resolve("chest-"+id+".nbt");}
    public void saveAll(){loaded.values().forEach(this::save);}
    private void save(ChestInventory inv){
        if(store.byId(inv.id)==null && store.archived(inv.id)==null)return;
        try {
            NbtCompound data=new NbtCompound();
            NbtList items=new NbtList();
            for(int i=0;i<54;i++){
                ItemStack stack=inv.getStack(i);
                if(stack.isEmpty())continue;
                NbtCompound entry=new NbtCompound();
                entry.putInt("Slot",i);
                entry.put("Stack",stack.encode(server.getRegistryManager()));
                items.add(entry);
            }
            data.put("Items",items);
            Path tmp=file(inv.id).resolveSibling("chest-"+inv.id+".nbt.tmp");
            NbtIo.writeCompressed(data,tmp);
            try{Files.move(tmp,file(inv.id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file(inv.id),StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){throw new IllegalStateException("Could not save guild chest!",e);}
    }
    private final class ChestInventory extends SimpleInventory {
        final String id;
        boolean loading;
        ChestInventory(String id){super(54);this.id=id;}
        @Override public void markDirty(){super.markDirty();if(!loading && (store.byId(id)!=null || store.archived(id)!=null))save(this);}
    }
}
