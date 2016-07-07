package cl.netgamer.endermail;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class MailAgent
{
	// PROPERTIES
	
	private Main main;
	private DataBase db;
	private ChatEvents ce;
	private String tableName;
	private String realName;
	private String lastLogin;
	private int expire;
	// last folder index, last page viewed, last message index
	private Map<String, int[]> views = new HashMap<String, int[]>();
	//private String[] f = new String[]{"inbox", "sent", "trash"};
	private Map<String, BukkitTask> cleanViewsTasks = new HashMap<String, BukkitTask>();
	private Map<String, Map<Integer, Integer>> idMaps = new HashMap<String, Map<Integer, Integer>>();
	private TablePrinter table = new TablePrinter("", 8, -3, 16, 29, 1, -4);
	
	// CONSTRUCTOR
	
	public MailAgent(Main main)
	{
		this.main = main;
		tableName =  main.getConfig().getString("DataSource.mySQLTablename");
		realName =  main.getConfig().getString("DataSource.mySQLRealName");
		lastLogin =  main.getConfig().getString("DataSource.mySQLColumnLastLogin");
		expire =  main.getConfig().getInt("expire");
		db = new DataBase(main);
		ce = new ChatEvents(main, this, expire);
		//is = new ItemSender();
	}
	
	// REGULAR METHODS
	
	// check unread mail count
	void check(CommandSender sender, boolean quiet)
	{
		if (sender != null)
		{
			// send queued items before and take note of free space
			String user = sender instanceof Player ? sender.getName() : "ADMIN";
			int unread = db.getInt("SELECT COUNT(muser) FROM endermail_folders WHERE muser='"+user+"' AND folder=0 AND unread=1;", null);
			if (unread != 0)
				sender.sendMessage("\u00A7EYou have "+unread+" unread mail messages");
			else if (!quiet)
				sender.sendMessage("\u00A7BYou have no unread mail messages");
			if (sender instanceof Player && ((Player) sender).getEnderChest().firstEmpty() < 0)
				sender.sendMessage("\u00A7EWarning: your enderchest is full.");
		}
	}
	
	// send new mail
	void sendMail(CommandSender sender, String[] args)
	{
		// insufficient parameters
		if (args.length < 2)
		{
			sender.sendMessage("\u00A7DInsufficient arguments: /mail sendmail recipients [subject]");
			return;
		}
		
		// get subject
		String subj = "";
		if (args.length > 1)
			for (int i = 2; i < args.length; ++i)
				subj += " "+args[i];
		else
			subj = " (no subject)";
		
		// jump to recipients validation
		sender.sendMessage("\u00A7ESend mail to "+ args[1]+", subject:"+subj);
		validate(sender, args[1], subj.trim(), "");
	}
	
	// validate recipients before composing
	// the only method that searches users in authme table
	private void validate(CommandSender sender, String recipients, String subject, String quote)
	{
		// pre-compose ..  check recipients, find recipients
		Statement sta = db.newStatement();
		String rcpt = "";
		String user;
		for (String to : recipients.replaceAll("'", "''").split(","))
		{
			if (to.equalsIgnoreCase("ADMIN"))
				rcpt += ",ADMIN";
			else if (!(user = db.getString("SELECT "+realName+" FROM "+tableName+" WHERE "+realName+"='"+to+"' AND "+lastLogin+"!=0;", sta)).isEmpty())
				rcpt += ","+user;
		}
		db.closeStatement(sta);
		
		// recipients not found
		if (rcpt.isEmpty())
		{
			sender.sendMessage("\u00A7DRecipient accounts could not be found.");
			return;
		}
		
		// at least 1 recipient found: input message from mc chat area
		ce.startDraft(sender, rcpt.substring(1), subject, quote);
	}
	
	// get message entered from ms chat area and deliver
	String deliver(List<String> draft)
	{
		// sender had regret
		if (draft == null)
			return "\u00A7ECancelled by user.";
		
		// draft: from, rcpt, subj, quote, attachment, body lines...
		Date now = new Date();
		String msg = "";
		
		// build new body from lines
		String brief = "";
		for(int i = 5; i < draft.size(); ++i)
		{	
			msg += "\n"+draft.get(i);
			if (brief.length() < 30)
				brief += draft.get(i)+" ";
		}
		
		// quote old body at the end
		if (!draft.get(3).isEmpty())
		{
			msg += "\n";
			for (String q : draft.get(3).split("\n"))
				msg += "\n>"+q;
		}
		
		// add headers to message
		brief = brief.substring(0, Math.min(30, brief.length())).replaceAll("'", "''");
		msg =
			"From: "+draft.get(0)+
			"\nTo: "+draft.get(1)+
			"\nSent: "+new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z (z)").format(now)+
			"\nSubject: "+draft.get(2)+
			(draft.get(4).isEmpty()?"":"\nAttachment: "+draft.get(4))+
			(msg.isEmpty()?"":"\n"+msg).replaceAll("'", "''");
		
		// insert message into db and get row id
		String query =
			"INSERT INTO endermail_messages VALUES (null, '"+
			draft.get(0)+"', '"+
			draft.get(1)+"', '"+
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now)+"', '"+
			draft.get(2).replaceAll("'", "''")+"', '"+
			brief+"...', '"+
			msg+"', '"+
			draft.get(4)+"');";
		Statement sta = db.newStatement();
		int id = db.getInt(query, sta);

		// send mail and notify
		for (String to : draft.get(1).split(","))
		{
			db.getResult("INSERT INTO endermail_folders VALUES ('"+to+"', 0, 1, "+id+");", sta);
			if (to.equalsIgnoreCase("ADMIN"))
				// add more consoles?
				check(main.getServer().getConsoleSender(), false);
			else
				check(main.getServer().getPlayerExact(to), false);
		}
		
		// store sent mail with valid recipients, for reference and future replyall, and return
		db.getResult("INSERT INTO endermail_folders VALUES ('"+draft.get(0)+"', 1, 0, "+id+");", sta);
		db.closeStatement(sta);
		return "\u00A7BMessage sent to valid recipients: "+draft.get(1);
	}
	
	private void refreshViews(final String user, int[] view)
	{
		// if view is null skip storing and pass to refresh task
		if (view != null)
			views.put(user, view);
		
		// cancel previous task
		if (cleanViewsTasks.containsKey(user))
			if (cleanViewsTasks.get(user) != null)
				cleanViewsTasks.get(user).cancel();
		
		// schedule later view deletion
		cleanViewsTasks.put(user, new BukkitRunnable()
		{
			@Override
			public void run()
			{
				views.remove(user);
				idMaps.remove(user);
				cleanViewsTasks.remove(user);
			}
		}.runTaskLater(main, expire));
	}
	
	void viewFolder(CommandSender sender, String folder)
	{
		// get current view: last folder, last page, last message
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		int[] view;
		if (views.containsKey(user))
			view = views.get(user);
		else
			view = new int[]{0, 1, 0};
		
		// change default folder?
		switch (folder)
		{
		case "inbox":
		case "i":
			view = new int[]{0, 1, 0};
			break;
		case "sent":
		case "s":
			view = new int[]{1, 1, 0};
			break;
		case "trash":
		case "t":
			view = new int[]{2, 1, 0};
		}
		
		// try db connection, get unread messages and last page
		Statement sta = db.newStatement();
		if (sta == null)
		{
			sender.sendMessage("\u00A7DDatabase connection error, contact server staff.");
			return;
		}
		int pages = Math.max(1, (int) Math.ceil(db.getInt("SELECT count(muser) FROM endermail_folders WHERE muser='"+user+"' AND folder="+view[0]+";", sta) / 8d));
		
		// change current page?
		switch (folder)
		{
		case "<<":
			view[1] = 1;
			break;
		case ">>":
			view[1] = pages;
			break;
		case "<":
			view[1] = Math.max(1, view[1]-1);
			break;
		case ">":
			view[1] = Math.min(pages, view[1]+1);
		}
		
		// save view, as folder and page has changed reset last viewed mail too
		view[2] = 0;
		refreshViews(user, view);
		int offset = view[1]*8-8;
		Map<Integer, Integer> mapId = new HashMap<Integer, Integer>();
		
		// print header then get folder page content
		table.print(sender, "\u00A7EID \t\u00A7E"+(view[0] == 1?"TO":"FROM")+"\t\u00A7ESUBJECT\t\t\u00A7EAGE");
		String body = "";
		String query =
			"SELECT f.id AS id, muser, unread, mfrom, mto, sent, subject, brief, attachment "+
			"FROM endermail_folders AS f, endermail_messages AS m "+
			"WHERE f.id=m.id AND muser='"+user+"' AND folder="+view[0]+" "+
			"ORDER BY id DESC LIMIT 8 OFFSET "+offset+";";
		ResultSet res = db.getResult(query, sta);
		String grey;
		String run;
		String toFrom;
		try
		{
			while (res.next())
			{
				mapId.put(++offset, res.getInt("id"));
				grey = res.getBoolean("unread")?"\u00A7F":"\u00A77";
				run = "`/mail "+offset;
				toFrom = (view[0] == 1?res.getString("mto"):res.getString("mfrom"));
				body += "\n"+
					grey+offset+" "+run+run+"\t"+
					grey+toFrom+"`"+toFrom+run+"\t"+
					grey+res.getString("subject")+"`"+res.getString("brief")+run+"\t"+
					grey+(res.getString("attachment").isEmpty()?"\t":"+`"+res.getString("attachment")+run+"\t")+
					grey+age(res.getTimestamp("sent"))+"`"+new SimpleDateFormat("MMMM d, yyyy HH:mm").format(res.getTimestamp("sent"))+run+"\t";
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			return;
		}
		// get unread messages before close db connection
		int unread = db.getInt("SELECT count(muser) FROM endermail_folders WHERE muser='"+user+"' AND folder=0 AND unread=1;", sta);
		db.closeStatement(sta);
		
		// print folder page content
		if (body.length() == 0)
			sender.sendMessage("\u00A77(empty folder)");
		else
			table.print(sender, body.substring(1));
		
		// print footer menu
		TablePrinter.printRaw(sender,
			"\u00A7EMenu : \t"+
			"\u00A7"+(view[1] == 1?"7<<":"B<<`first page`/mail <<")+"\t\u00A7"+(view[1] == 1?"7 < \t":"B < `previous page`/mail <\t")+
			"\u00A7E"+view[1]+"/"+pages+"\t"+
			"\u00A7"+(view[1] == pages?"7 > ":"B > `next page`/mail >")+"\t\u00A7"+(view[1] == pages?"7>>\t":"B>>`last page`/mail >>\t")+
			"\u00A7"+(view[0] == 0?"D":"B")+" Inbox("+unread+") `inbox folder`/mail inbox\t"+
			"\u00A7"+(view[0] == 1?"D":"B")+" Sent `sent folder`/mail sent\t"+
			"\u00A7"+(view[0] == 2?"D":"B")+" Trash `trash folder`/mail trash\t"+
			"\u00A77:\t\u00A7B SendMail... `\u00A77/mail sendmail \u00A7Dplayer1,player2... \u00A7Bsubject ...`/mail sendmail `\t\u00A77:\t\u00A7B Help `help`/mail help");
		
		// cache message numbers
		idMaps.put(user, mapId);
	}
	
	void viewMessgage(CommandSender sender, int pos)
	{
		// try to get id if exists
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		if (!idMaps.containsKey(user) || !idMaps.get(user).containsKey(pos))
		{
			sender.sendMessage("\u00A7DI forgot the folder you were browsing, browse again.");
			return;
		}
		views.get(user)[2] = pos;
		refreshViews(user, null);
		pos = idMaps.get(user).get(pos);
		
		// retrieve message from db and mark as read
		Statement sta = db.newStatement();
		String msg = db.getString("SELECT message FROM endermail_messages WHERE id="+pos+";", sta);
		db.getResult("UPDATE endermail_folders SET unread=0 WHERE muser='"+user+"' AND folder="+views.get(user)[0]+" AND id="+pos+";", sta);
		db.closeStatement(sta);
		
		// print mail message with blank lines
		for (String l : msg.split("\n"))
			sender.sendMessage("\u00A77"+l);
		
		// print bottom menu
		TablePrinter.printRaw(sender,
			"\u00A7EMenu : \t"+
			"\u00A7B\u00A7L .. `back to folder page`/mail ..\t"+
			"\u00A7B REply `\u00A77/mail reply...`/mail reply\t"+
			"\u00A7B ReplyAll `\u00A77/mail replyall...`/mail replyall\t"+
			"\u00A7B ForWard... `\u00A77/mail forward \u00A7Dplayer1,player2...`/mail forward `\t"+
			"\u00A77:\t\u00A7B Delete `send to trash`/mail del\t"+
			"\u00A7B SendMail... `\u00A77/mail sendmail \u00A7Dplayer1,player2... \u00A7Bsubject ...`/mail sendmail `\t\u00A77:\t\u00A7B Help `help`/mail help");
	}
	
	void delete(CommandSender sender, String... args)
	{
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		
		// with no arguments try to get last readed mail
		if (args.length == 1)
		{
			int id;
			if (views.containsKey(user) && (id = views.get(user)[2]) != 0)
			{
				delete(user, ""+id);
			}
			else
				sender.sendMessage("\u00A7DI forgot the message you were reading, read again.");
			return;
		}
		
		// try to find specified mail ids
		if (!idMaps.containsKey(user))
		{
			sender.sendMessage("\u00A7DI forgot the folder you were browsing, browse again.");
			return;
		}
		
		int index;
		Map<Integer, Integer> map = idMaps.get(user);
		
		String ids = "";
		String pos = "";
		for (String arg : args)
		{
			try
			{
				index = Integer.parseInt(arg);
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			
			if (map.containsKey(index))
			{
				ids += ", "+map.get(index);
				pos += " "+index;
			}
		}
		sender.sendMessage("\u00A7BRecent listed mail deleted:"+pos+".");
		if (!ids.isEmpty())
			delete(user, ids.substring(2));
	}
	
	private void delete(String user, String ids)
	{
		// move from inbox to trash (update folder field)
		Statement sta = db.newStatement();
		db.getResult("UPDATE endermail_folders SET folder=2 WHERE muser='"+user+"' AND folder=0 AND id IN ("+ids+");", sta);
		
		// destroy remaining last mails (8 mails per page, 9 pages = 72 mails)
		int remain = db.getInt("SELECT COUNT(muser) FROM endermail_folders WHERE muser='"+user+"' AND folder=2;", sta) - 72;
		if (remain > 0)
			db.getResult("DELETE FROM endermail_folders WHERE muser='"+user+"' AND folder=2 ORDER BY id ASC LIMIT "+remain+";", sta);
		
		// destroy messages which are not in folders
		db.getResult("DELETE FROM endermail_messages WHERE id NOT IN (SELECT id FROM endermail_folders);", sta);
		db.closeStatement(sta);
	}
	
	void forward(CommandSender sender, String[] args)
	{
		// insufficient parameters
		if (args.length < 2)
		{
			sender.sendMessage("\u00A7DInsufficient arguments: /mail forward to1,to2...");
			return;
		}
		
		// get last mail internal id
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		int id;
		if (!views.containsKey(user) || (id = views.get(user)[2]) == 0 || !idMaps.containsKey(user) || !idMaps.get(user).containsKey(id))
		{
			sender.sendMessage("\u00A7DI forgot the message you opened.");
			return;
		}
		id = idMaps.get(user).get(id);
		
		/* // get last mail id
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		int id;
		if (!views.containsKey(user) || (id = views.get(user)[2]) == 0)
		{
			sender.sendMessage("\u00A7DI forgot the message you opened.");
			return;
		} */
		
		// get last subject and raw message
		String subject = "Fw: ";
		String quote = "";
		Statement sta = db.newStatement();
		ResultSet res = db.getResult("SELECT subject, message FROM endermail_messages WHERE id="+id+";", sta);
		try
		{
			res.next();
			subject += res.getString("subject");
			quote = res.getString("message");
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		db.closeStatement(sta);
		
		sender.sendMessage("\u00A7EForward mail to "+ args[1]+", subject: "+subject);
		validate(sender, args[1], subject, quote);
	}
	
	void reply(CommandSender sender, String arg)
	{
		// get last mail internal id
		String user = sender instanceof Player ? sender.getName() : "ADMIN";
		int id;
		if (!views.containsKey(user) || (id = views.get(user)[2]) == 0 || !idMaps.containsKey(user) || !idMaps.get(user).containsKey(id))
		{
			sender.sendMessage("\u00A7DI forgot the message you opened.");
			return;
		}
		id = idMaps.get(user).get(id);
		
		// were viewing the "sent" folder
		if (views.get(user)[0] == 1)
		{
			sender.sendMessage("\u00A7DYou can't reply to yourself.");
			return;
		}
		
		// get last sender, subject and raw message
		String recipients = "";
		String subj = "Re: ";
		String quote = "";
		Statement sta = db.newStatement();
		ResultSet res = db.getResult("SELECT mfrom, mto, subject, message FROM endermail_messages WHERE id="+id+";", sta);
		try
		{
			res.next();
			recipients = res.getString("mfrom");
			if (arg.contains("a")) // replyall || ra
				recipients += ","+res.getString("mto");
			subj += res.getString("subject");
			quote = res.getString("message");
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		db.closeStatement(sta);
		
		recipients = recipients.replaceAll(","+user+"(,|$)", "$1");
		sender.sendMessage("\u00A7EReply mail to "+ recipients+", subject: "+subj);
		validate(sender, recipients, subj, quote);
	}
	
	/**
	 * input text format: tabs make any sense on mc chat area so fields are tab delimited<br/>
	 * field format: properties are delimited with grave accents inside fields<br/>
	 * fields can handle "section sign" format codes, console does not support tooltips or links
	 * text`tooltip`command`
	 * - text: displayed text, mandatory
	 * - tooltip: if present will add "hoverEvent" + "showText"
	 * - command: if present will add "clickEvent" + "run_command", or "suggest_command" if ends with "`"
	 * you can't skip to command using an empty tooltip because String.split() discards empty array elements
	 * @param raw
	 * @return json text for tellraw command
	 */
	private String age(Date sent)
	{
		// timestamp difference, then cycle years, Months, days, hours, minutes and seconds
		long age = (new Date().getTime() - sent.getTime()) / 1000;
		if (age >= 31104000L)
			return String.format("%dy", age / 31104000L);
		if (age >= 2592000L)
			return String.format("%dM", age / 2592000L);
		if (age >= 86400L)
			return String.format("%dd", age / 86400L);
		if (age >= 3600L)
			return String.format("%dh", age / 3600L);
		if (age >= 60L)
			return String.format("%dm", age / 60L);
		return String.format("%ds", age);
	}
	
}
