package dev.silver.cobbleguilds;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Standard vanilla chest transfer logic; no synthetic item buttons inside this inventory. */
public final class GuildMenus {
    private GuildMenus(){}
    private static GuildChest chests;
    public static void init(GuildChest store){chests=store;}
    public static void openChest(ServerPlayerEntity player,GuildService svc,Guild g,boolean admin){
        if((g.closed && !(admin && svc.isAdmin(player,"cobbleguilds.admin.manage") && svc.store.archived(g.id)==g)) ||
            (!g.closed && !svc.authorized(player,g,"chest",admin))){
            GuildService.tell(player,"You don't have permission to open the guild chest.");return;
        }
        Inventory inventory=chests.inventory(g);
        int rows=g.chestRows;
        ScreenHandlerType<?> type=switch(rows){
            case 1->ScreenHandlerType.GENERIC_9X1;case 2->ScreenHandlerType.GENERIC_9X2;
            case 3->ScreenHandlerType.GENERIC_9X3;case 4->ScreenHandlerType.GENERIC_9X4;
            case 5->ScreenHandlerType.GENERIC_9X5;default->ScreenHandlerType.GENERIC_9X6;};
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,playerInv,view)->
            new GuardedChest(type,syncId,playerInv,inventory,rows,svc,g,admin),Text.literal(g.color+g.name+" Guild Chest")));
    }
    private static final class GuardedChest extends GenericContainerScreenHandler {
        private final GuildService svc;
        private final Guild guild;
        private final boolean admin;
        GuardedChest(ScreenHandlerType<?> type,int syncId,PlayerInventory player,Inventory inventory,int rows,GuildService svc,Guild guild,boolean admin){
            super(type,syncId,player,inventory,rows);this.svc=svc;this.guild=guild;this.admin=admin;
        }
        @Override public boolean canUse(PlayerEntity p){
            return p instanceof ServerPlayerEntity player && (guild.closed
                ? admin && svc.store.archived(guild.id)==guild && svc.isAdmin(player,"cobbleguilds.admin.manage")
                : svc.store.byId(guild.id)==guild && svc.authorized(player,guild,"chest",admin));
        }
        @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity p){
            if(!canUse(p))return;
            super.onSlotClick(slot,button,action,p);
        }
        @Override public ItemStack quickMove(PlayerEntity p,int index){
            if(!canUse(p))return ItemStack.EMPTY;
            return super.quickMove(p,index);
        }
    }
}
