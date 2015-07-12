package ru.myxomopx.mimic

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.json.simple.parser.JSONParser
import ru.dpohvar.varscript.workspace.Workspace

/**
 * Created by OPX at 010 10.07.15.
 */
class MimicChestService {
    private static MimicChestService instance;
    private final Workspace workspace;
    private String mimicChestName = "&eMimic".c
    HashMap<Block,MimicChestPart> mimicParts = [:]

    public MimicChestService(Workspace workspace){
        instance = this
        this.workspace = workspace
        startListen()
    }

    private void startListen(){
        workspace.listen(PlayerInteractEvent,{ PlayerInteractEvent event ->
            if (event.clickedBlock == null) return
            if (!(event.player.gameMode in [GameMode.ADVENTURE, GameMode.SURVIVAL])) return
            def block = event.clickedBlock
            if (!MimicUtils.chestTypes.contains(block.type)) return
            if (!(block.state as Chest).inventory.getTitle().startsWith(mimicChestName)) return
            def action = event.action
            event.cancelled = true
            if (action == Action.RIGHT_CLICK_BLOCK) {
                if (mimicParts[block] instanceof MimicChestAttacker){
                    def part = mimicParts[block]
                    if (part.isDestroyed()) {
                        def eater = new MimicChestEater(this,block,event.player,workspace.generator())
                        mimicParts[block] = eater
                    }
                    return
                }
                if (!mimicParts.containsKey(block) || mimicParts[block].isDestroyed()){
                    def eater = new MimicChestEater(this,block,event.player,workspace.generator())
                    mimicParts[block] = eater
                    return
                } else {
                    def eater = mimicParts[block] as MimicChestEater
                    if (eater.isOpen()) eater.closeChest()
                    else eater.openChest()
                    return
                }
            }
            event.cancelled = false
        })

        workspace.listen(BlockBreakEvent,{BlockBreakEvent event ->
            def block = event.block
            if (!MimicUtils.chestTypes.contains(block.type)) return
            if (!(block.state as Chest).inventory.title.startsWith(mimicChestName)) return
            event.cancelled = true
            def part = mimicParts[block]
            if (part instanceof MimicChestAttacker){
                part.onTakeDamage(1)
                return
            }
            if (part instanceof MimicChestEater) {
                part.onDestroy(true)
            }
            mimicParts[block] = new MimicChestAttacker(this,block,workspace.generator(),getCreatingParams(block)) // maybe will be other HP
            if (part?.health) {
                mimicParts[block].health = part.health
            }
        })

        workspace.listen(EntityExplodeEvent){EntityExplodeEvent event ->
            event.blockList().findAll{ block ->
                if (!MimicUtils.chestTypes.contains(block.type)) return false
                if (!(block.state as Chest).inventory.title.startsWith(mimicChestName)) return false
                return true
            }.each { Block block ->
                event.blockList().remove(block)
                def part = mimicParts[block]
                if (part == null) {
                    mimicParts[block] = new MimicChestAttacker(this,block, workspace.generator(), getCreatingParams(block))
                    return
                }
                if (part instanceof MimicChestAttacker) {
                    part.onTakeDamage(1)
                    return
                }
                if (part instanceof MimicChestEater) {
                    part.onDestroy(true)
                    mimicParts[block] = new MimicChestAttacker(this,block, workspace.generator(), getCreatingParams(block))
                    if (part?.health) {
                        mimicParts[block].health = part.health
                    }
                }

            }
        }

        workspace.listen(BlockExplodeEvent){BlockExplodeEvent event ->
            event.blockList().findAll{ block ->
                if (!MimicUtils.chestTypes.contains(block.type)) return false
                if (!(block.state as Chest).inventory.title.startsWith(mimicChestName)) return false
                return true
            }.each { Block block ->
                event.blockList().remove(block)
                def part = mimicParts[block]
                if (part == null) {
                    mimicParts[block] = new MimicChestAttacker(this,block, workspace.generator(), getCreatingParams(block))
                    return
                }
                if (part instanceof MimicChestAttacker) {
                    part.onTakeDamage(1)
                    return
                }
                if (part instanceof MimicChestEater) {
                    part.onDestroy(true)
                    mimicParts[block] = new MimicChestAttacker(this,block, workspace.generator(), getCreatingParams(block))
                    if (part?.health) {
                        mimicParts[block].health = part.health
                    }
                }

            }
        }


    }

    public ItemStack getMimicItem(){
        def mimicItem = new ItemStack(Material.CHEST)
        def m = mimicItem.itemMeta
        m.displayName = mimicChestName
        mimicItem.itemMeta = m
        return mimicItem
    }


    public MimicChestPart getMimicPart(Block block){
        return mimicParts[block]
    }

    public void destroyMimic(Block block, boolean becauseBroken){
        def part = mimicParts[block]
        part?.onDestroy(becauseBroken)
        if (part instanceof MimicChestEater && becauseBroken) {
            mimicParts[block] = new MimicChestAttacker(this,block,workspace.generator(),getCreatingParams(block))
            return
        }
        mimicParts.remove(block)
    }

    public MimicChestEater createNewEater(Block block, Player player, Double health = null){
        if (mimicParts.containsKey(block)) return null
        mimicParts[block] = new MimicChestEater(this,block,player,workspace.generator(),health)
    }

    public MimicChestAttacker createNewAttacker(Block block, Map params = getCreatingParams(block)){
        if (mimicParts.containsKey(block)) return null
        mimicParts[block] = new MimicChestAttacker(this,block,workspace.generator(),params)
    }

    JSONParser parser = new JSONParser()
    public Map<String,Object> getCreatingParams(Block block){
        if (!MimicUtils.chestTypes.contains(block.type)) return [:]
        if (!(block.state as Chest).inventory.title.startsWith(mimicChestName)) return [:]
        def nameParts = (block.state as Chest).inventory.title.split(" ")
        if (nameParts.size()==1) return [:]
        def jsonString = nameParts[1]
        try{
            return parser.parse(jsonString) as Map<String,Object>
        } catch (Exception e) {
            println("Can't parse mimic params [$block.x:$block.y:$block.z]")
        }
        return [:]
    }

    public static MimicChestService getInstance(){
        return instance
    }
}
