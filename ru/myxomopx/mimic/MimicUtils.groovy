package ru.myxomopx.mimic

import net.minecraft.server.v1_9_R1.BlockPosition as NMS_BlockPosition
import net.minecraft.server.v1_9_R1.Block as NMS_Block
import net.minecraft.server.v1_9_R1.PacketPlayOutBlockAction as NMS_PacketPlayOutBlockAction
import net.minecraft.server.v1_9_R1.PacketPlayOutEntityEquipment as NMS_PacketPlayOutEntityEquipment
import net.minecraft.server.v1_9_R1.EnumItemSlot as NMS_EnumItemSlot
import net.minecraft.server.v1_9_R1.EnumParticle as NMS_EnumParticle
import net.minecraft.server.v1_9_R1.PacketPlayOutWorldParticles as NMS_PacketPlayOutWorldParticles

import org.bukkit.craftbukkit.v1_9_R1.inventory.CraftItemStack as CB_CraftItemStack

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector


public class MimicUtils {

    static def chestTypes = [Material.CHEST,Material.TRAPPED_CHEST]

    public static void openChest(Block block, boolean silent=false){
        if (!chestTypes.contains(block.type)) return

        def blockPosition = new NMS_BlockPosition(block.x as int,block.y as int,block.z as int)
        def nmsBlock = NMS_Block.getById(block.typeId)
        def packet = new NMS_PacketPlayOutBlockAction(blockPosition,nmsBlock,1,1)
        broadcastPacket(packet)
        if (!silent) block.world.playSound(block.loc, Sound.BLOCK_CHEST_OPEN, 1, 1)
    }

    public static void closeChest(Block block, boolean silent=false){
        if (!chestTypes.contains(block.type)) return
        def blockPosition = new NMS_BlockPosition(block.x as int,block.y as int,block.z as int)
        def nmsBlock = NMS_Block.getById(block.typeId)
        def packet = new NMS_PacketPlayOutBlockAction(blockPosition,nmsBlock,1,0)
        broadcastPacket(packet)
        if (!silent) block.world.playSound(block.loc, Sound.BLOCK_CHEST_CLOSE, 1 ,1)
    }

    private static broadcastPacket(def packet, Player ... playersIgnore){
        Bukkit.getOnlinePlayers().findAll {!(it in playersIgnore)}.each {
            it.handle.playerConnection.sendPacket(packet)
        }
    }

    public static void sendFakePlayerEquipment(Player player, ItemStack itemStack){
        sendPlayerEquipment(player,itemStack,NMS_EnumItemSlot.HEAD)
        sendPlayerEquipment(player,null,NMS_EnumItemSlot.MAINHAND)
        sendPlayerEquipment(player,null,NMS_EnumItemSlot.OFFHAND)
        sendPlayerEquipment(player,null,NMS_EnumItemSlot.LEGS)
        sendPlayerEquipment(player,null,NMS_EnumItemSlot.CHEST)
        sendPlayerEquipment(player,null,NMS_EnumItemSlot.FEET)
    }

    public static void sendRealPlayerEquipment(Player player){
        sendPlayerEquipment(player,player.helmet,NMS_EnumItemSlot.HEAD)
        sendPlayerEquipment(player,player.hand,NMS_EnumItemSlot.MAINHAND)
        sendPlayerEquipment(player,player.inventory.itemInOffHand,NMS_EnumItemSlot.OFFHAND)
        sendPlayerEquipment(player,player.boots,NMS_EnumItemSlot.FEET)
        sendPlayerEquipment(player,player.legs,NMS_EnumItemSlot.LEGS)
        sendPlayerEquipment(player,player.armor,NMS_EnumItemSlot.CHEST)
    }

    private static void sendPlayerEquipment(Player player, ItemStack itemStack, NMS_EnumItemSlot slot){
        def packet = new NMS_PacketPlayOutEntityEquipment(
                player.id as int,slot,
                itemStack?CB_CraftItemStack.asNMSCopy(itemStack):null
        )
        broadcastPacket(packet,player)
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
        def particle = NMS_EnumParticle.valueOf(particleName)
        def packet = new NMS_PacketPlayOutWorldParticles(particle,true,
            location.x as float, location.y as float, location.z as float,
            dif.x as float, dif.y as float, dif.z as float,
            speed as float, count as int, new int[0]
        )
        broadcastPacket(packet)
    }
}