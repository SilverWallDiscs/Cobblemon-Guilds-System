package dev.silver.cobbleguilds;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.Base64;

/** Full server-side ItemStack component/NBT snapshot, including registered modded items. */
public final class GuildRewardCodec {
    private GuildRewardCodec(){}
    /** Human-readable item name, including custom names and a fallback for modded lang keys absent on the dedicated server. */
    public static String displayName(ItemStack stack,String itemId){
        if(stack.isEmpty())return "Unknown Item";
        String translated=stack.getName().getString();
        if(!translated.isBlank() && !translated.equals(itemId) && !translated.matches("(?:item|block)\\.[a-z0-9_.-]+"))return translated;
        String id=itemId==null?"unknown_item":itemId.substring(itemId.lastIndexOf(':')+1);
        String[] words=id.split("[_ .-]+");StringBuilder human=new StringBuilder();
        for(String word:words)if(!word.isEmpty()){
            if(!human.isEmpty())human.append(' ');
            human.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return human.isEmpty()?"Unknown Item":human.toString();
    }
    public static String displayName(DynamicRegistryManager registries,String itemId,String encoded,int quantity){
        try{return displayName(decode(registries,encoded,itemId,quantity),itemId);}
        catch(RuntimeException exception){
            String path=itemId==null?"Unknown Item":itemId.substring(itemId.lastIndexOf(':')+1);
            StringBuilder label=new StringBuilder();
            for(String word:path.split("[_ .-]+"))if(!word.isEmpty()){
                if(!label.isEmpty())label.append(' ');
                label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return label.isEmpty()?"Unknown Item":label.toString();
        }
    }
    public static String encode(ItemStack item,DynamicRegistryManager registries){
        if(item.isEmpty()||item.getCount()<1||item.getCount()>64||item.getCount()>item.getMaxCount())throw new IllegalArgumentException("Hold an item stack of 1-64 items");
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            NbtElement encoded=item.encode(registries);
            if(!(encoded instanceof NbtCompound compound))throw new IllegalArgumentException("Item did not encode to compound NBT");
            NbtIo.writeCompressed(compound,bytes);
            if(bytes.size()>16384)throw new IllegalArgumentException("Reward item data exceeds 16 KB");
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException e){throw new IllegalStateException("Could not encode reward",e);}
    }
    public static ItemStack decode(DynamicRegistryManager registries,String encoded,String itemId,int amount){
        if(encoded!=null && !encoded.isBlank()){
            try {
                byte[] bytes=Base64.getDecoder().decode(encoded);
                if(bytes.length>16384)throw new IllegalArgumentException("Oversized reward");
                NbtCompound nbt=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtSizeTracker.of(65536));
                ItemStack stack=ItemStack.fromNbt(registries,nbt).orElse(ItemStack.EMPTY);
                if(stack.isEmpty()||amount<1||amount>stack.getMaxCount())throw new IllegalArgumentException("Unknown or oversized reward item");
                stack.setCount(amount);
                return stack;
            }catch(Exception e){throw new IllegalArgumentException("Cannot decode configured reward item",e);}
        }
        Identifier id=Identifier.tryParse(itemId==null?"":itemId);
        if(id==null || !Registries.ITEM.containsId(id))throw new IllegalArgumentException("Unknown item: "+itemId);
        Item item=Registries.ITEM.get(id);
        if(item==Items.AIR)throw new IllegalArgumentException("Invalid reward item");
        if(amount<1||amount>64||amount>item.getMaxCount())throw new IllegalArgumentException("Reward exceeds item stack limit");
        return new ItemStack(item,amount);
    }
}
