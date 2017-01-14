package ru.myxomopx.mimic

import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import ru.dpohvar.varscript.extension.region.SphereRegion
import ru.dpohvar.varscript.trigger.BukkitIntervalTrigger
import ru.dpohvar.varscript.trigger.TriggerContainer

class MimicChestAttacker extends MimicChestPart{
    private final double maxHealth;
    private TriggerContainer attackContainer;
    private final double scanRadius;
    private final SphereRegion scanSphere;
    private Long firstNoEnemyDate;
    private final Location attackLocation;
    private final boolean displayAttackZone;

    public MimicChestAttacker(MimicChestService service, Block block, TriggerContainer container, Map<String,Object> params=[:]){
        super(service,block,container)

        if (params["maxHealth"]) maxHealth = params["maxHealth"] as double
        else maxHealth = 20
        if (params["health"]) health = params["health"] as double
        else health = maxHealth
        if (params["maxAttackDelay"]) maxAttackDelay = params["maxAttackDelay"] as int
        else maxAttackDelay = 40
        if (params["scanRadius"]) scanRadius = params["scanRadius"] as double
        else scanRadius = 12
        if (params["displayAttackZone"]) displayAttackZone = params["displayAttackZone"] as boolean
        else displayAttackZone = false

        attackDelay = maxAttackDelay
        attackLocation = block.loc.add(0.5,1.2,0.5)
        attackContainer = container.generator()
        mount.world.playSound(mount,Sound.ENTITY_GHAST_SCREAM  ,2,1)
        scanSphere = mount.sphere(scanRadius)
        canBreakBlockSphere = mount.sphere(5)

        if (displayAttackZone) attackContainer.interval(5){displayAttackZone()}
        attackContainer.timeout(20){attack()}
        attackContainer.interval(20){ // if 30 seconds no enemies near - transforms to Eater
            if (getRandomTarget() == null){
                if (firstNoEnemyDate == null) {
                    firstNoEnemyDate = new Date().time
                    return
                }
                if (new Date().time - firstNoEnemyDate >= 30000){
                    MimicChestService.instance.destroyMimic(block,false)
                    return
                }
                return
            }
            firstNoEnemyDate = null
        }
    }

    public void onTakeDamage(double damage){
        health -= damage
        mount.world.playSound(mount,Sound.ENTITY_WITHER_HURT,2,0.7 as float)

        def dmg = getTakenDamage()
        if (dmg < 10){
            attackDelay = maxAttackDelay
        } else if (dmg < 20) {
            attackDelay = maxAttackDelay/2
        } else if (dmg < 30) {
            attackDelay = maxAttackDelay/3
        } else {
            attackDelay = maxAttackDelay/4
        }
        eatingContainer?.stop()
        shulkerContainer?.stop()
        throwPlayersAway(canBreakBlockSphere.players.findAll {
            it.gameMode in [GameMode.SURVIVAL,GameMode.ADVENTURE] && !it.dead
        })

        if (health <= 0) MimicChestService.instance.destroyMimic(block,true)
    }

    private void displayAttackZone(){
        scanSphere.blocks.findAll { block ->
            block.type != Material.AIR && block.getRelative(0,1,0).type == Material.AIR
        } each { block ->
            MimicUtils.playParticle(block.loc.add(0.5,1.02,0.5),"CRIT_MAGIC",new Vector(0,0,0),1 as float,0)
        }
    }

    private Player getRandomTarget(){
        scanSphere.players.findAll {
            it.gameMode in [GameMode.SURVIVAL,GameMode.ADVENTURE] && !it.dead
        }.rnd()
    }

    private double getTakenDamage(){
        return maxHealth-health
    }

    private final long maxAttackDelay
    private long attackDelay
    private SphereRegion canBreakBlockSphere; // sphere where players can break chest


    private void attack(){
        List<Player> playersAroundChest = canBreakBlockSphere.players.findAll {
            it.gameMode in [GameMode.SURVIVAL,GameMode.ADVENTURE] && !it.dead
        }
        if (playersAroundChest.size() > 0){
            def rnd = Math.floor(Math.random()*5)
            switch (rnd){
                case 0:
                    attack_barfZombie()
                    break;
                case 1:
                    attack_launchFireball(playersAroundChest.rnd())
                    break;
                case 2:
                    attack_flameThrower(playersAroundChest.rnd())
                    break;
                case 3:
                    attack_tongue(playersAroundChest.rnd())
                    break;
                case 4:
                    attack_shulker(2)
                    break;
            }
            return
        }
        def target = getRandomTarget()
        if (target == null) {
            attackContainer.timeout(20,this.&attack)
            return
        }

        def rnd = Math.floor(Math.random()*6)
        switch (rnd){
            case 0:
                attack_launchFireball(target)
                break;
            case 1:
                attack_barfTNT(target)
                break;
            case 2:
                attack_launchArrow(target)
                break;
            case 3:
                attack_barfZombie()
                break;
            case 4:
                attack_tongue(target)
                break;
            case 5:
                attack_shulker(5)
                break;
        }

    }

    private double tongueStepSize = 0.3
    private int tongueLength = 15
    private double tongueRadius = 0.5
    private TriggerContainer eatingContainer
    private void attack_tongue(Player player){ // DONE
        openChest(false)
        def currentLoc = block.loc.add(0.5,0.7,0.5)
        eatingContainer = attackContainer.generator()
        ArrayList<Location> tongueLocations = [];
        def tongueMove = {
            def vector = currentLoc.to(getPlayerMiddle(player)).setLen(tongueStepSize)
            currentLoc.add(vector)
            def block = currentLoc.block
            if (block.type.solid && block != this.block) {
                currentLoc.add(vector*-1.0)
                return
            }
            tongueLocations.add(currentLoc.clone())
        }

        eatingContainer.interval(1){
            tongueLocations.eachWithIndex { loc, index ->
                def blue = 0;
                def green = 0.5;
                def red = 1
                MimicUtils.playParticle(loc,"REDSTONE",new Vector(red,green,blue),1 as float,0)
            }
        }
        BukkitIntervalTrigger eatScanTrigger,tongueCreator, eatingPlayer;
        Player eatenPlayer = null;

        eatingContainer.stopHook {
            eatenPlayer?.allowFlight = false
            closeChest()
            attackContainer.timeout(attackDelay,this.&attack)
            eatingContainer = null
        }

        def eatingPlayerProcess = { Player target, int locIndex ->
            if (Material.SHIELD in [target.hand.type,target.inventory.itemInOffHand.type] && target.blocking) {
                target.sound(Sound.BLOCK_ANVIL_LAND)
                def shieldItem = player.hand.type == Material.SHIELD ? player.hand : player.inventory.itemInOffHand
                if (shieldItem.durability >= Material.SHIELD.maxDurability-100) {
                    if (player.hand.type == Material.SHIELD) {
                        player.hand = null
                    } else {
                        player.inventory.itemInOffHand = null
                    }
                } else {
                  shieldItem.durability += 100;
                }
                eatingContainer.stop()
                closeChest()
                return ;
            }
            eatenPlayer = target
            target.setAllowFlight(true)
            eatScanTrigger.stop()
            tongueCreator.stop()
            tongueLocations = tongueLocations[0..locIndex]
            eatingPlayer = eatingContainer.interval(1){
                if (target.dead) {
                    eatingContainer.stop()
                    return
                }
                if(tongueLocations.size() <= 5) {
                    eatingContainer.stop()
                    closeChest()
                    eatPlayer(target)
                    return
                }
                tongueLocations = tongueLocations[0..-6]
                target >> tongueLocations.last()
            }
        }


        eatScanTrigger = eatingContainer.interval(1) {
            for (int i = 0; i < tongueLocations.size(); i++){
                def loc = tongueLocations[i]
                def players = scanPlayers(loc,tongueRadius)
                if (players) {
                    eatingPlayerProcess(players.rnd(),i)
                    break;
                }
            }
        }

        eatingContainer.timeout(5){
            tongueLocations.add(currentLoc.clone())
            tongueCreator = eatingContainer.interval(1){ int i, BukkitIntervalTrigger trigger ->
                if (i >= (tongueLength/tongueStepSize)){
                    eatingContainer.stop()
                }
                tongueMove()
            }

        }
    }

    private void eatPlayer(Player player){
        MimicChestService.instance.destroyMimic(block,false)
        MimicChestService.instance.createNewEater(block,player,health)
    }

    private List<Player> scanPlayers(Location location, double radius){
        List<Player> returnList = []
        List<Player> players = location.sphere(2).players
        players.each {
            if (it.location.distance(location) <= radius) returnList.add(it)
            else if (it.eye.distance(location) <= radius) returnList.add(it)
            else if (getPlayerMiddle(it).distance(location) < radius) returnList.add(it)
        }
        return returnList
    }

    private static double fireThrowerStep = 0.4
    private static double fireThrowerRadius = 1.2
    private void attack_flameThrower(Player player){
        openChest()
        attackContainer.timeout(5){
            closeChest()
            attackContainer.timeout(attackDelay,this.&attack)
            def loc = (player.eye | player.location)
            def vector = loc-attackLocation
            def stepCount = vector.length()/fireThrowerStep*2
            def currentLoc = attackLocation.clone()
            BukkitIntervalTrigger trigger;
            trigger = attackContainer.interval(1){int iteration ->
                if (iteration >= stepCount) {
                    trigger.stop()
                    return
                }
                MimicUtils.playParticle(currentLoc,"FLAME",new Vector(0.3,0.3,0.3),0 as float,10)
                def players = scanPlayers(currentLoc,fireThrowerRadius)
                if (players.size() > 0) {
                    players.each {
                        it.setFire(100+(Math.random()*100) as int)
                    }
                }
                currentLoc.add(vector.setLen(fireThrowerStep))
            }
        }
    }

    private void attack_launchFireball(Player target){ // DONE
        openChest()
        attackContainer.timeout(5){
            closeChest()
            attackContainer.timeout(attackDelay,this.&attack)

            def loc = attackLocation.clone();
            def vector = getPlayerMiddle(target)-attackLocation
            loc.direction = vector
            loc.spawn(Fireball)
        }
    }

    private TriggerContainer shulkerContainer;
    private void attack_shulker(int bulletCount){ // DONE
        openChest()
        shulkerContainer = attackContainer.generator();
        shulkerContainer.stopHook {
            closeChest()
            shulkerContainer.timeout(attackDelay,this.&attack)
        }
        shulkerContainer.timeout(5){
            shulkerContainer.interval(10){ int i, def interval ->
                def target = getRandomTarget();
                if (!target || i >= bulletCount) {
                    closeChest()
                    shulkerContainer.timeout(attackDelay,this.&attack)
                    interval.stop();
                    shulkerContainer = null;
                    return
                }
                if (!target.isOnline() || attackLocation.distance(target.loc) > scanRadius) return;
                def bullet = attackLocation.spawn(ShulkerBullet)as ShulkerBullet;
                mount.sound(Sound.ENTITY_PLAYER_BURP)
                bullet.setTarget(target)
            }
        }
    }

    private void attack_launchArrow(Player target){ // DONE
        openChest()
        attackContainer.timeout(5){
            closeChest()
            attackContainer.timeout(attackDelay,this.&attack)
            def loc = attackLocation.clone();
            def vector = target.eye-attackLocation
            loc.direction = vector
            def arrow = loc.spawn(Arrow)
            arrow.velocity = vector/4.0
        }
    }

    private void throwPlayersAway(List<Player> targets){ // DONE
        openChest()
        attackContainer.timeout(5){
            closeChest()
            MimicUtils.getCircle(mount,1.5,16).each {
                 MimicUtils.playParticle(it,"CLOUD",new Vector(),0.5 as float,5)
            }
            mount.sound(Sound.ENTITY_BAT_TAKEOFF,1,0.1)
            targets.each {
                def vel = (it.loc-mount.loc).normalize()*2
                vel.setY(0.5)
                it.velocity = vel
                it.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING,200,3))
            }
        }
    }

    private void barfEntity(Entity entity, double power=0.5){
        if (entity == null) {
            return
        }
        entity >> mount; // TP entity to mount
        openChest(true)
        triggerContainer.timeout(5){
            mount.sound(Sound.ENTITY_PLAYER_BURP)
            entity.setVelocity(barfVector*power)
            closeChest(true)
        }
    }

    private void attack_barfZombie(){ // DONE
        if (scanSphere.entities.findAll {it instanceof Zombie}.size() >= 2 ) {
            attackContainer.timeout(1,this.&attack)
            return
        }
        def zombie = attackLocation.spawn(Zombie) as Zombie
        zombie.setBaby(true)
        zombie.canPickupItems = false
        zombie.equipment.helmetDropChance = 0
        zombie.equipment.chestplateDropChance = 0
        zombie.equipment.leggingsDropChance = 0
        zombie.equipment.bootsDropChance = 0
        zombie.equipment.itemInHand = null
        zombie.equipment.itemInHandDropChance = 0
        zombie.equipment.helmet = new ItemStack(Material.CHEST)
        barfEntity(zombie,0.7)
        attackContainer.timeout(attackDelay,this.&attack)
    }

    List<PotionEffectType> badEffects = [
            PotionEffectType.BLINDNESS,PotionEffectType.POISON,PotionEffectType.POISON,PotionEffectType.SLOW_DIGGING,
            PotionEffectType.POISON,PotionEffectType.POISON,PotionEffectType.POISON,PotionEffectType.SLOW_DIGGING,
            PotionEffectType.LEVITATION,PotionEffectType.LEVITATION,PotionEffectType.WITHER,
            PotionEffectType.SLOW_DIGGING,PotionEffectType.WITHER,PotionEffectType.WITHER,PotionEffectType.WITHER
    ]

    private void attack_barfPotion(Player target){
        openChest()
        triggerContainer.timeout(5){
            closeChest()
            attackContainer.timeout(attackDelay,this.&attack)

            mount.sound(Sound.ENTITY_PLAYER_BURP)
            def potion = attackLocation.spawn(ThrownPotion) as ThrownPotion
            def itemPotion = new ItemStack(Material.SPLASH_POTION)
            PotionMeta meta = itemPotion.itemMeta as PotionMeta
            meta.basePotionData.
            meta.addCustomEffect(new PotionEffect(badEffects.rnd(),Math.random()*100+60 as int,0),true)
            itemPotion.itemMeta = meta
            potion.setItem(itemPotion)
            potion.setVelocity((getPlayerMiddle(target)-potion.loc))
        }
    }

    private void attack_barfTNT(Player target){ // DONE
        openChest()
        triggerContainer.timeout(5){
            closeChest()
            attackContainer.timeout(attackDelay,this.&attack)

            mount.sound(Sound.ENTITY_PLAYER_BURP)
            def tnt = attackLocation.spawn(TNTPrimed) as TNTPrimed
            tnt.setIsIncendiary(true)
            tnt.yield = 2.2
            tnt.setVelocity((getPlayerMiddle(target)-tnt.loc).normalize()*0.7)
        }
    }

    private static Location getPlayerMiddle(Player player){
        return player.loc | player.eye
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


    public void openChest(boolean silent=false){
        MimicUtils.openChest(block,silent)
    }

    public void closeChest(boolean silent=false){
        MimicUtils.closeChest(block,silent)
    }


    private void playDeathEffect(List<ItemStack> items){
        def firework = mount.spawn(Firework) as Firework
        def fwMeta = firework.fireworkMeta
        Random random = new Random()
        5.times {
            def effectBuilder = FireworkEffect.builder()
            effectBuilder.flicker(random.nextBoolean())
            effectBuilder.trail(random.nextBoolean())
            effectBuilder.withColor(Color.fromRGB(random.nextInt(255),random.nextInt(255),random.nextInt(255)))
            fwMeta.addEffect(effectBuilder.build())
        }
        fwMeta.power = 126
        firework.fireworkMeta = fwMeta
        triggerContainer.timeout(20){
            firework.detonate()
            def loc = firework.loc
            items.each {
                loc.spawn(it).setVelocity(Vector.random*0.3)
            }
            triggerContainer.stop()
        }

    }

    @Override
    void onDestroy(boolean becauseDestroyed) {
        destroyed = true
        if (becauseDestroyed) {
            mount.sound(Sound.ENTITY_ZOMBIE_HORSE_DEATH,1,1)
            List<ItemStack> items = chest.inventory.contents;
            chest.inventory.clear()
            items.remove(0)
            items = items.findAll {it != null}
            block.setBlock(0)
            mount.ex(2)
            playDeathEffect(items)
        } else {
            triggerContainer.stop()
        }
        attackContainer.stop()
    }

}
