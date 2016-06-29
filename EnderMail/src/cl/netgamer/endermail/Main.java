package cl.netgamer.endermail;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin implements Listener
{
	// PROPERTIES
	
	private MailAgent ma;
	
	// ENABLE PLUGIN
	
	public void onEnable()
	{
		saveDefaultConfig();
		ma = new MailAgent(this);
	}
	
	// EXECUTE COMMANDS
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args)
	{
		// unrelated command
		if (!cmd.getName().equalsIgnoreCase("mail"))
			return true;
		
		// default action
		// check and help, the only subcommands working anywhere
		String arg;
		if (args.length > 0)
			arg = args[0].toLowerCase();
		else
			arg = isNearAnEnderchest(sender) ? "inbox" : "check";
		
		// before check enderchest proximity
		
		// help pages
		if (arg.matches("^(help|h|\\?)$"))
		{
			if (args.length == 1)
				Help.page(sender, "1");
			else
				Help.page(sender, args[1]);
			return true;
		}

		// check mail
		if (arg.equals("check"))
		{
			ma.check(sender, (args.length > 1 && !args[1].equalsIgnoreCase("quiet")));
			return true;
		}
		
		// next commands need an enderchest near
		if (!isNearAnEnderchest(sender))
		{
			sender.sendMessage("\u00A7DYou must be near an enderchest to use this feature.");
			return true;
		}
		
		// loop subcommands
		switch (arg)
		{
		// new message
		case "sendmail":
		case "sm":
			ma.sendMail(sender, args);
			return true;
		// browse folders
		case "inbox":
		case "i":
		case "sent":
		case "s":
		case "trash":
		case "t":
		case ">":
		case "<":
		case ">>":
		case "<<":
		case "..":
			ma.viewFolder(sender, arg);
			return true;
		// delete (recycle) message
		case "delete":
		case "d":
			ma.delete(sender, args);
			return true;
		// forward message
		case "forward":
		case "fw":
			ma.forward(sender, args);
			return true;
		// reply(all) message
		case "reply":
		case "re":
		case "replyall":
		case "ra":
			ma.reply(sender, arg);
			return true;
			
		}
		
		// check possible number entered (read message by id), or unrecognized sub command
		try
		{
			ma.viewMessgage(sender, Integer.parseInt(arg));
		}
		catch(NumberFormatException e)
		{
			sender.sendMessage("\u00A7DUnrecognized subcommand. type '/mail help' for info");
		}
		return true;
	}
	
	// check enderchest proximity method
	private boolean isNearAnEnderchest(CommandSender sender)
	{
		if (sender instanceof Player)
		{
			// use eye distance instead
			Location loc = ((Player) sender).getLocation();
			Location xx = loc.clone().add(-5, -5, -5);
			Location yy;
			Location zz;
			for (int x = 1; x <= 11; ++x)
			{
				yy = xx.clone();
				for (int y = 1; y <= 11; ++y)
				{
					zz = yy.clone();
					for (int z = 1; z <= 11; ++z)
					{
						if (zz.getBlock().getType() == Material.ENDER_CHEST && zz.distanceSquared(loc) <= 25)
							return true;
						zz = zz.add(0, 0, 1);
					}
					yy = yy.add(0, 1, 0);
				}
				xx = xx.add(1, 0, 0);
			}
			return false;
		}
		// server console are always considered near an enderchest for commands to work
		return true;
	}
	
}
