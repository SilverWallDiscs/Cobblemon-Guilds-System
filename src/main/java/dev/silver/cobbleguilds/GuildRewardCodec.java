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
                if(stack.isEmpty()||stack.getCount()<1||stack.getCount()>stack.getMaxCount())throw new IllegalArgumentException("Unknown or oversized reward item");
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
