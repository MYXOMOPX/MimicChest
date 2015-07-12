package ru.myxomopx.mimic

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.dpohvar.varscript.utils.ItemStackUtils
import ru.dpohvar.varscript.utils.ReflectionUtils.RefClass
import ru.dpohvar.varscript.utils.ReflectionUtils.RefConstructor
import ru.dpohvar.varscript.utils.ReflectionUtils.RefMethod
import org.bukkit.util.Vector

import static ru.dpohvar.varscript.utils.ReflectionUtils.getRefClass

public class MimicUtils {
    private static RefClass cBlockPosition = getRefClass("{nms}.BlockPosition")
    private static RefConstructor nBlockPosition = cBlockPosition.getConstructor(int,int,int)
    private static RefClass cPacketPlayOutBlockAction = getRefClass("{nms}.PacketPlayOutBlockAction")
    private static RefClass cNMSBlock = getRefClass("{nms}.Block")
    private static RefConstructor nPacketPlayOutBlockAction = cPacketPlayOutBlockAction.getConstructor(cBlockPosition,cNMSBlock,int,int)
    private static RefClass cPacketPlayOutEntityEquipment = getRefClass("{nms}.PacketPlayOutEntityEquipment")
    private static RefClass cNMSItemStack = getRefClass("{nms}.ItemStack")
    private static RefConstructor nPacketPlayOutEntityEquipment = cPacketPlayOutEntityEquipment.getConstructor(int,int,cNMSItemStack)
    private static RefMethod mGetById = cNMSBlock.findMethodByName("getById")
    private static RefClass cPacketPlayOutWorldParticles = getRefClass("{nms}.PacketPlayOutWorldParticles")
    private static RefClass cEnumParticle = getRefClass("{nms}.EnumParticle")
    private static RefConstructor nPacketPlayOutWorldParticles = cPacketPlayOutWorldParticles.findConstructor(11)

    static def chestTypes = [Material.CHEST,Material.TRAPPED_CHEST]

    public static void openChest(Block block, boolean silent=false){
        if (!chestTypes.contains(block.type)) return
        def blockPosition = nBlockPosition.create(block.x as int,block.y as int,block.z as int)
        def nmsBlock = mGetById.call(block.typeId)
        def packet = nPacketPlayOutBlockAction.create(blockPosition,nmsBlock,1,1)
        broadcastPacket(packet)
        if (!silent)block.world.playSound(block.loc, Sound.CHEST_OPEN,1,1)
    }

    public static void closeChest(Block block, boolean silent=false){
        if (!chestTypes.contains(block.type)) return
        def blockPosition = nBlockPosition.create(block.x as int,block.y as int,block.z as int)
        def nmsBlock = mGetById.call(block.typeId)
        def packet = nPacketPlayOutBlockAction.create(blockPosition,nmsBlock,1,0)
        broadcastPacket(packet)
        if (!silent)block.world.playSound(block.loc, Sound.CHEST_CLOSE,1,1)
    }

    private static broadcastPacket(def packet){
        Bukkit.getOnlinePlayers().each {
            it.handle.playerConnection.sendPacket(packet)
        }
    }

    public static void sendFakePlayerEquipment(Player player, ItemStack itemStack){
        sendPlayerEquipment(player,itemStack,4)
        sendPlayerEquipment(player,null,0)
        sendPlayerEquipment(player,null,1)
        sendPlayerEquipment(player,null,2)
        sendPlayerEquipment(player,null,3)
    }

    public static void sendRealPlayerEquipment(Player player){
        sendPlayerEquipment(player,player.helmet,4)
        sendPlayerEquipment(player,player.hand,0)
        sendPlayerEquipment(player,player.boots,1)
        sendPlayerEquipment(player,player.legs,2)
        sendPlayerEquipment(player,player.armor,3)
    }

    private static void sendPlayerEquipment(Player player, ItemStack itemStack, int slot){
        def packet = nPacketPlayOutEntityEquipment.create(player.id as int,slot as int,ItemStackUtils.itemStackUtils.createNmsItemStack(itemStack))
        broadcastPacket(packet)
    }

    public static ArrayList<Location> getCircle(Location center, double radius, int amount){
        World world = center.getWorld();
        double increment = (2 * Math.PI) / amount;
        ArrayList<Location> locations = new ArrayList<Location>();
        for(int i = 0;i < amount; i++)
        {
            double angle = i * increment;
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            locations.add(new Location(world, x, center.getY(), z));
        }
        return locations;
    }

    public static void playParticle(Location location, String particleName, Vector dif, float speed, int count){
        def particle = cEnumParticle.realClass.valueOf(particleName)
        def packet = nPacketPlayOutWorldParticles.create(particle,true,
            location.x as float, location.y as float, location.z as float,
            dif.x as float, dif.y as float, dif.z as float,
            speed as float, count as int, new int[0]
        )
        broadcastPacket(packet)
    }
}