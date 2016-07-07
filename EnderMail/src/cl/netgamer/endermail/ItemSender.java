package cl.netgamer.endermail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.mojang.authlib.GameProfile;

public class ItemSender
{
	Main main = null;
	Class<?> minecraftServerClass = null;
	Class<?> worldClass = null;
	Class<?> playerInteractManagerClass = null;
	Class<?> entityPlayerClass = null;
	Object minecraftServerInstance = null;
	Object worldServerInstance = null;
	Object playerInteractManagerInstance = null;
	Constructor<?> entityPlayerConstructor = null;
	
	public ItemSender(Main main)
	{
		this.main = main;
		try
		{
			String version = Bukkit.getServer().getClass().getName().split("\\.")[3];
			minecraftServerClass = Class.forName("net.minecraft.server."+version+".MinecraftServer");
			worldClass = Class.forName("net.minecraft.server."+version+".World");
			playerInteractManagerClass = Class.forName("net.minecraft.server."+version+".PlayerInteractManager");
			entityPlayerClass = Class.forName("net.minecraft.server."+version+".EntityPlayer");
			minecraftServerInstance = minecraftServerClass.getMethod("getServer").invoke(Bukkit.getServer());
			worldServerInstance = minecraftServerClass.getMethod("getWorldServer", int.class).invoke(minecraftServerInstance, 0);
			playerInteractManagerInstance = playerInteractManagerClass.getConstructor(worldClass).newInstance(worldServerInstance);
			entityPlayerConstructor = entityPlayerClass.getConstructor(minecraftServerClass, worldServerInstance.getClass(), GameProfile.class, playerInteractManagerClass);
		}
		catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | InstantiationException e)
		{
			e.printStackTrace();
		}
	}
	
	String sendItem(CommandSender sender, String recipient)
	{
		// only can send items between players
		if (!(sender instanceof Player) || recipient.equalsIgnoreCase("ADMIN"))
		{
			sender.sendMessage("\u00A7DSending items only allowed between players.");
			return "";
		}
		
		// if giver is holding no item
		Player giver = (Player) sender;
		ItemStack held = giver.getInventory().getItemInMainHand();
		if (held == null || held.getType() == Material.AIR)
			return "";
		
		if (recipient.contains(","))
		{
			sender.sendMessage("\u00A7DSending items only allowed to single players.");
			return "";
		}
		
		// try to load (offline) player receiver
		OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(recipient);
		boolean online = offPlayer.isOnline();
		final Player receiver = loadPlayer(offPlayer);
		if (receiver == null)
		{
			sender.sendMessage("\u00A7Can't find destination enderchest.");
			return "";
		}
		
		// actually try to send the item
		String itemName = held.getType()+" x"+held.getAmount();
		//System.out.println("HELD #"+held+"#");
		Map<Integer, ItemStack> remain = receiver.getEnderChest().addItem(held);
		//receiver.updateInventory();
		//System.out.println("REMAIN #"+remain+"#");
		if (remain.size() != 0)
		{
			//itemName = "\u00A7Ddestination enderchest full";
			giver.getInventory().setItemInMainHand(remain.get(0));
			itemName += " (partial)";
		}
		else
			giver.getInventory().setItemInMainHand(null);
		
		// clean things if receiver were offline
		if (!online)
		{
			receiver.saveData();
			//receiver.kickPlayer("");
			
			// some posts recommend this to avoid "async player kick exception"
			// seems to throws with never logged in players in craftbukkit running instance
			// prints "+" in chat, maybe letting player loaded
			// this will kick loaded player in main thread
			Bukkit.getScheduler().runTask(main, new Runnable()
			{
				public void run()
				{
					receiver.kickPlayer("");
				}
			});
		}
		sender.sendMessage("\u00A7BItem stack attached: "+itemName);
		return itemName;
	}
	
	/*
	 * // Example Usage
	 * PlayerLoader pl = new PlayerLoader();
	 * OfflinePlayer offPlayer = getOfflinePlayerSomeway();
	 * 
	 * // take note of previous status before try to load the (offline) player data
	 * boolean online = offPlayer.isOnline();
	 * Player loadedPlayer = pl.loadPlayer(offPlayer);
	 * if (loadedPlayer == null)
	 *   return;
	 * 
	 * // do whatever you want with your (loaded) player
	 * loadedPlayer.doSomething();
	 * 
	 *  // leave player in previous connection state before (save and) return
	 * 	if (!online)
	 * 	{
	 * 	  loadedPlayer.saveData();
	 *    loadedPlayer.kickPlayer("");
	 * 	}
	 */
	private Player loadPlayer(OfflinePlayer offPlayer)
	{
		if (offPlayer.isOnline())
			return (Player) offPlayer;
		
		// comment this if you rely on previous verifications and want to create local player data
		//if (!offPlayer.hasPlayedBefore())
		//	return null;
		
		Player player = null;
		try
		{
			GameProfile gameProfile = new GameProfile(offPlayer.getUniqueId(), offPlayer.getName());
			Object playerInteractManagerInstance = playerInteractManagerClass.getConstructor(worldClass).newInstance(worldServerInstance);
			Object entityPlayerInstance = entityPlayerConstructor.newInstance(minecraftServerInstance, worldServerInstance, gameProfile, playerInteractManagerInstance);
			player = (Player) entityPlayerClass.getMethod("getBukkitEntity").invoke(entityPlayerInstance);
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | InstantiationException e)
		{
			e.printStackTrace();
		}
		
		if (player != null)
			player.loadData();
		return player;
	}
	
}
