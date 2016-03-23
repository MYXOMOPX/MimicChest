package ru.myxomopx.mimic

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.Chest
import ru.dpohvar.varscript.trigger.TriggerContainer;


abstract class MimicChestPart {
    protected boolean destroyed;
    public final Block block
    protected final TriggerContainer triggerContainer
    protected final Location mount
    protected final Chest chest
    abstract public void onDestroy(boolean becauseBroken)
    protected final MimicChestService service;
    Double health;

    MimicChestPart(MimicChestService service, Block block, TriggerContainer triggerContainer){
        def state = block.state
        this.service = service
        this.block = block
        this.triggerContainer = triggerContainer
        this.mount = block.location.add(0.5,1.2,0.5);
        if (!(state instanceof Chest)) throw new RuntimeException("Can't create MimicPart for non-chest block [$block.x:$block.y:$block.z]")
        this.chest = state
    }

    public final isDestroyed(){
        return destroyed
    }
}

