package cl.netgamer.endermail;

import org.bukkit.command.CommandSender;

public class Help
{
	
	static void page(CommandSender sender, String page)
	{
		/*
		 * cyan    \\u00A7B
		 * magenta \\u00A7D
		 * yellow  \\u00A7E
		 * grey    \\u00A77
		 * white   \\u00A7F
		 * reset   \\u00A7R
		 */
		
		switch (page)
		{
		case "4":
			TablePrinter.printRaw(sender,
				"\u00A7ECompose mode :\n"+
				"\u00A7FCapture chat input to emulate a line editor.\n"+
				"\u00A7FExit with one of these single line commands :\n"+
				"\u00A7B\u00A7L. \t\u00A7F: Send your message\n"+
				"\u00A7B+ \t\u00A7F: Attach held item and send message\n"+
			    "\u00A7Bc \t\u00A7F: Cancel the operation\n"+
			    "\u00A7F* Attachments are sent to [offline] player enderchest\n"+
				"\u00A7EMenu : \t"+
				"\u00A7B<<`first page`/mail help 1\t\u00A7B < `previous page`/mail help 3\t"+
				"\u00A7E4/4\t"+
				"\u00A77 > \t\u00A77>>\t"+
				"\u00A7B\u00A7L .. `back to folder page`/mail ..");
			break;
		case "3":
			TablePrinter.printRaw(sender,
				"\u00A7ESubcommands for send messages :\n"+
				"\u00A7Bsendmail (players) [subject ...]\t\u00A7F : Compose and send a message\n"+
				"\u00A7Breply[all]\t\u00A7F : Compose and reply current message [to all]\n"+
				"\u00A7Bforward (players)\t\u00A7F : Compose and forward current message\n"+
				"\u00A7F* (players) = Comma separated player names (no spaces!)\n"+
				"\u00A7EMenu : \t"+
				"\u00A7B<<`first page`/mail help 1\t\u00A7B < `previous page`/mail help 2\t"+
				"\u00A7E3/4\t"+
				"\u00A7B > `next page`/mail help 4\t\u00A7B>>`last page`/mail help 4\t"+
				"\u00A7B\u00A7L .. `back to folder page`/mail ..");
			break;
		case "2":
			TablePrinter.printRaw(sender,
				"\u00A7ESubcommands for manage messages :\n"+
				"\u00A7B(number)\t\u00A7F : View message by number, from current page\n"+
				"\u00A7Bdelete [messages]\t\u00A7F : Move [messages] or current one to trash\n"+
				"\u00A7B\u00A7L.. \t\u00A7F: Back to last cached folder page\n"+
				"\u00A7F* [messages] = Message numbers separated by spaces\n"+
				"\u00A7EMenu : \t"+
				"\u00A7B<<`first page`/mail help 1\t\u00A7B < `previous page`/mail help 1\t"+
				"\u00A7E2/4\t"+
				"\u00A7B > `next page`/mail help 3\t\u00A7B>>`last page`/mail help 4\t"+
				"\u00A7B\u00A7L .. `back to folder page`/mail ..");
			break;
		default:
			TablePrinter.printRaw(sender,
				"\u00A7B/mail\t\u00A7E : Friendly and powerful mail plugin (alias /m)\n"+
				"\u00A7F* Near an enderchest = Open inbox folder\n"+
				"\u00A7F* Far an enderchest = Check new mail\n"+
				"\u00A7FBasic subcommands :\n"+
				"\u00A7Bhelp [1-4]\t\u00A7F : Show these help pages by number\n"+
				"\u00A7B(inbox | sent | trash)\t\u00A7F : Open that folder at first page\n"+
				"\u00A7B(<< | < | > | >>)\t\u00A7F : Load and browse pages from current folder\n"+
				"\u00A7EMenu : \t"+
				"\u00A77<<\t\u00A77 < \t"+
				"\u00A7E1/4\t"+
				"\u00A7B > `next page`/mail help 2\t\u00A7B>>`last page`/mail help 4\t"+
				"\u00A7B\u00A7L .. `back to folder page`/mail ..");
		}
	}
}
