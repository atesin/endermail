package cl.netgamer.endermail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ChatEvents implements Listener 
{
	private Main main;
	private ItemSender is;
	private int expire;
	private MailAgent ma;
	private Map<String, List<String>> drafts = new HashMap<String, List<String>>();
	private Map<String, BukkitTask> cleanDraftsTasks = new HashMap<String, BukkitTask>();
	
	public ChatEvents(Main main, MailAgent ma, int expire)
	{
		this.main = main;
		is = new ItemSender(main);
		main.getServer().getPluginManager().registerEvents(this, main);
		this.expire = expire;
		this.ma = ma;
	}
	
	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent e)
	{
		if (drafting(e.getPlayer(), e.getMessage()))
			e.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e)
	{
		if (drafting(e.getPlayer(), e.getMessage()))
			e.setCancelled(true);
	}
	
	@EventHandler
	public void onServerCommand(ServerCommandEvent e)
	{
		// ServerCommandEvent does not cancel 
		// https://hub.spigotmc.org/jira/browse/SPIGOT-1111
		// https://www.spigotmc.org/threads/reading-input-from-console.81770/#post-918128
		if (drafting(e.getSender(), e.getCommand()))
			e.setCommand("donothing");
	}
	
	/////////  REGULAR METHODS
	
	// start drafting
	void startDraft(CommandSender sender, String recipients, String subject, String quote)
	{
		// draft: from, rcpt, subj, quote, attachment, body lines...
		//TablePrinter.printRaw(sender, "Type your message,\t\u00A7B\u00A7L . `send with a single dot`.\t\u00A7E: send,\t\u00A7B + `attach held item and send`+\t\u00A7E: attach and send,\t\u00A7B c `cancel with a single 'c'`c\t\u00A7E: cancel");
		TablePrinter.printRaw(sender,
			"\u00A7EType your message,\t"+
			"\u00A7B\u00A7L . `send mail with a single dot`.\t\u00A7E: send,\t"+
			"\u00A7B + `attach held item and send mail`+\t\u00A7E: attach,\t"+
			"\u00A7B z `discard last line`+\t\u00A7E: undo,\t"+
			"\u00A7B c `cancel and return to chat`c\t\u00A7E: cancel");
		List<String> body = new ArrayList<String>();
		body.add(sender instanceof Player ? sender.getName() : "ADMIN");
		body.add(recipients);
		body.add(subject);
		body.add(quote);
		body.add("");
		drafts.put(sender instanceof Player ? sender.getName() : "ADMIN", body);
		resetExpire(sender);
	}
	
	private void resetExpire(final CommandSender sender)
	{
		// cancel future deletion task
		final String user = sender instanceof Player ? sender.getName() : "ADMIN";
		if (cleanDraftsTasks.containsKey(user))
			cleanDraftsTasks.get(user).cancel();
		
		// schedule new draft deletion task
		cleanDraftsTasks.put(user, new BukkitRunnable()
		{
			@Override
			public void run()
			{
				drafts.remove(user);
				cleanDraftsTasks.remove(user);
				if (sender != null)
					sender.sendMessage("\u00A7EComposing message cancelled due timeout.");
			}
		}.runTaskLater(main, expire));
	}

	private boolean drafting(CommandSender sender, String line)
	{
		//System.out.println("COMMAND #"+line+"#");
		
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		if (!drafts.containsKey(user))
			return false;
		
		if (line.matches("^(\\.|\\+|c|C|z|Z)$"))
		{
			List<String> draft = null;
			
			if (!line.equalsIgnoreCase("c"))
			{
				// draft: from, rcpt, subj, quote, attachment, body lines...
				draft = drafts.get(user);
				
				// undo (discard) last line and return not exit composing yet
				if (line.equalsIgnoreCase("z"))
				{
					if (draft.size() > 5)
					{
						draft.remove(draft.size()-1);
						sender.sendMessage("\u00A7E(Last line discarded)");
					}
					return true;
				}
				
				if (line.equals("+"))
					draft.set(4, is.sendItem(sender, draft.get(1)));
				
				// send message anyway
			}	
			
			// delete scheduled removal task, draft itself, and deliver message
			if (cleanDraftsTasks.containsKey(user))
			{
				cleanDraftsTasks.get(user).cancel();
				cleanDraftsTasks.remove(user);
			}
			drafts.remove(user);
			sender.sendMessage(ma.deliver(draft));
			//ma.viewFolder(sender, "..");
			return true;
		}
		
		// store line in message
		resetExpire(sender);
		if (sender instanceof Player)
			sender.sendMessage("\u00A77"+line);
		drafts.get(user).add(line);
		return true;
	}
	
}
