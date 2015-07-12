package ru.myxomopx.mimic

import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import ru.dpohvar.varscript.trigger.TriggerContainer

class MimicChestEater extends MimicChestPart{
    private final Inventory inventory
    private final Location teleportLocation
    private TriggerContainer eatingContainer
    private boolean isOpen = false;
    private double eatItemChance = 0.5;

    private Player eatenPlayer
    private GameMode eatenPlayerGameMode;
    private boolean eatenPlayerAllowedFly;

    public MimicChestEater(MimicChestService service, Block block, Player awakener, TriggerContainer workspace, Double health = null){
        super(service,block,workspace)
        this.health = health
        eatingContainer = workspace.generator()
        inventory = chest.inventory
        teleportLocation = block.loc.add(0.5,-0.9,0.5)
        eatenPlayer = awakener
        generatePlayerHead(awakener) // puts to 0 slot players head (Else it's would not load skull skin)

        openChest(true)
        eatingContainer.timeout(2){
            closeChest(false)
            playBurpSound(mount)
        }

        eatenPlayerGameMode = awakener.gameMode
        awakener >> teleportLocation
        awakener.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,Integer.MAX_VALUE,1))
        MimicUtils.sendFakePlayerEquipment(awakener,getPlayerHead())
        awakener.gameMode = GameMode.ADVENTURE
        eatenPlayerAllowedFly = awakener.getAllowFlight()
        awakener.setAllowFlight(true)


        eatingContainer.interval(20){ // 1 second
            awakener.damage(0.5)
            if (Math.random() < eatItemChance ){
                eatPlayerItem()
            }
        }

        eatingContainer.listen(PlayerMoveEvent, this.&onPlayerMove)
        eatingContainer.listen(InventoryClickEvent, this.&onInvClick)
        eatingContainer.listen(PlayerDeathEvent, this.&onPlayerDeath)
        eatingContainer.listen(PlayerQuitEvent, this.&onPlayerQuit)
        eatingContainer.listen(PlayerDropItemEvent, this.&onItemDrop)
        eatingContainer.listen(EntityDamageByEntityEvent, this.&onPlayerAttack)

        eatingContainer.interval(1){
            awakener.setFlying(true)
        }

    }

    private void eatPlayerItem(){
        Inventory inv = eatenPlayer.inventory
        List<Integer> slots = []
        def contents = inv.contents
        for (int i = 0; i < contents.size(); i++){
            if (contents[i] != null) slots.add(i)
        }
        if (eatenPlayer.helmet) slots.add(-4)
        if (eatenPlayer.armor) slots.add(-3)
        if (eatenPlayer.legs) slots.add(-2)
        if (eatenPlayer.boots) slots.add(-1)
        if (!slots) return
        def slot = slots.rnd()
        ItemStack itemStack;
        if (slot == -4) {
            itemStack = eatenPlayer.helmet
            eatenPlayer.helmet = null
        } else if (slot == -3) {
            itemStack = eatenPlayer.armor
            eatenPlayer.armor = null
        } else if (slot == -2) {
            itemStack = eatenPlayer.legs
            eatenPlayer.legs = null
        } else if (slot == -1) {
            itemStack = eatenPlayer.boots
            eatenPlayer.boots = null
        } else {
            itemStack = contents[slot]
            inv.setItem(slot,null)
        }
        eatItem(itemStack)
    }

    private void onInvClick(InventoryClickEvent event){
        if (event.whoClicked == eatenPlayer) {
            event.cancelled = true
        }
    }

    private void onPlayerMove(PlayerMoveEvent event){
        if(event.player != eatenPlayer) return
        if (event instanceof PlayerTeleportEvent) {
            event.cancelled = true
            return
        }
        double fX = event.from.x, fY = event.from.y, fZ = event.from.z;
        double tX = event.to.x, tY = event.to.y, tZ = event.to.z;
        if (fX != tX || fY != tY || tZ != fZ) {
            event.to = event.from
        };
    }

    private onPlayerDeath(PlayerDeathEvent event){
        if(event.entity != eatenPlayer) return
        List<ItemStack> items = event.drops.clone() as List<ItemStack>
        event.drops.clear()
        println("ITEMS: ${items}")
        items.each {eatItem(it)}
        def skull = mount.spawn(new ItemStack(Material.SKULL_ITEM))
        barfEntity(skull)
        MimicChestService.instance.destroyMimic(block,false)
        if (health != null) {
            def attacker = MimicChestService.instance.createNewAttacker(block)
            attacker.health = health
        }
    }

    private onPlayerQuit(PlayerQuitEvent event){
        if(event.player != eatenPlayer) return
        def player = eatenPlayer
        player.kill()      // Calls OnPlayerDeath
        eatenPlayer = null
    }

    private onItemDrop(PlayerDropItemEvent event){
        if(event.player != eatenPlayer) return
        eatItem(event.itemDrop.itemStack)
        event.itemDrop.remove()
    }

    private onPlayerAttack(EntityDamageByEntityEvent event){
        if (event.damager != eatenPlayer) return
        if (!open) event.cancelled = true
    }

    private void eatItem(ItemStack itemStack){
        if (inventory.firstEmpty() == -1){
            def item = mount.spawn(itemStack)
            barfEntity(item)
        } else {
            inventory.addItem(itemStack)
            mount.world.playSound(mount.loc,Sound.EAT,1,1);
        }
    }

    private void barfEntity(Entity entity){
        if (entity == null) {
            print("BARFING NULL!!!")
            return
        }
        entity >> mount; // TP entity to mount
        MimicUtils.openChest(block,true)
        triggerContainer.timeout(5){
            playBurpSound(mount)
            entity.setVelocity(barfVector*0.5)
            MimicUtils.closeChest(block,true)
        }
    }

    private Vector getBarfVector(){
        def data = block.data;
        switch (data){
            case 4:
                return new Vector(-1, Math.random()*0.3, Math.random()*0.6-0.3)
            case 2:
                return new Vector(Math.random()*0.6-0.3, Math.random()*0.3, -1)
            case 5:
                return new Vector(1, Math.random()*0.3, Math.random()*0.6-0.3)
            case 3:
                return new Vector(Math.random()*0.6-0.3, Math.random()*0.3, 1)
        }
        return new Vector(0,1,0)
    }


    private static void playBurpSound(Location loc){
        loc.world.playSound(loc,Sound.BURP,1,1);
    }

    public boolean isOpen(){
        return isOpen
    }

    public void openChest(boolean silent=false){
        MimicUtils.openChest(block,silent)
        isOpen = true
        eatenPlayer.removePotionEffect(PotionEffectType.BLINDNESS)
        MimicUtils.sendFakePlayerEquipment(eatenPlayer,getPlayerHead())
    }

    public void closeChest(boolean silent=false){
        MimicUtils.closeChest(block,silent)
        isOpen = false
        eatenPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,Integer.MAX_VALUE,0))
        MimicUtils.sendFakePlayerEquipment(eatenPlayer,null)
    }

    private Inventory fakeInventory;
    private void generatePlayerHead(Player player) {
        if (!fakeInventory) fakeInventory = Bukkit.createInventory(chest,9)
        ItemStack itemStack = new ItemStack(Material.SKULL_ITEM,1,3 as short)
        SkullMeta meta = itemStack.itemMeta;
        meta.owner = player.name
        meta.displayName = "Mimic's head!"
        itemStack.itemMeta = meta
        fakeInventory.setItem(0, itemStack)
    }

    private ItemStack getPlayerHead(){
        return fakeInventory.getItem(0)
    }


    @Override
    void onDestroy(boolean becauseBroken) {
        destroyed = true
        eatingContainer.stop()
        if (eatenPlayer != null) {
            barfEntity(eatenPlayer)
            eatenPlayer.allowFlight = eatenPlayerAllowedFly
            eatenPlayer.gameMode = eatenPlayerGameMode
            eatenPlayer.activePotionEffects.each {
                eatenPlayer.removePotionEffect(it.type)
            }
            MimicUtils.sendRealPlayerEquipment(eatenPlayer)
            eatenPlayer = null
        }
    }
}