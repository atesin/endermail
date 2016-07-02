package cl.netgamer.endermail;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
<pre>
@version 2
@author atesin#gmail,com


Class to print vertically aligned text tables for minecraft chat area and server console, based on TextTable. 
GPLv3 sep2013, some ideas from Erine's implementation which has additional features (http://ee5.net/?p=520).

FEATURES

- Once tablePrinter is created it can be used to print many texts into same format tables (reusable)
- This class handles format codes EXCEPT BOLD FORMAT that may break alignments.
  More info: <a>http://minecraft.gamepedia.com/Formatting_codes</a>.
- Includes a static method to send json messages delimited by grave accents, see below
- Table convertion supports described text links, considering just printable part

RECENT CHANGES

- Minor syntax fixes

USAGE:

- Create your TablePrinter object with your desired table structure in mind
- Build a csv like text, with fields delimited by tabs (\t) and lines by newlines (\n).
- Optionally each field can have a tooltip and a command as explained above
- Optionally you can query how many pages will have your text before convert
- Print your text 


- With this text create a TextTab object, specifying default format code and column widths (line = 53chars)
- Specify page height and get resulting number of pages, optionally you can sort it
- Retrieve some page, for server console or chat area output, fixed or variable width fonts

<i>
TablePrinter myPrinter = new TablePrinter("\u00A7E",(int) height, -10, 15); // can omit last left aligned width 
String myText = "HEAD1\tHEAD2\tHEAD3\n" + "\u00A7Cdata1\tdata2\tdata3`show`/list";
int pages = countPages(myText); // optional
myPrinter.print((CommandSender) myReader, (int) myPage);  // if page ommited will print all, useful for console
</i>

PRINT TEXT LINKS:

As a bonus, this class provides a static method to print text links to do more friendly interfaces
The link attributes must be delimited with grave accents ("printed text`tooltip`command")
If link ends with ` will be suggested instead, More info at <a>http://minecraft.gamepedia.com/Commands#Raw_JSON_text</a>

<i>
String withTooltip = "server software`bukkit";
String withCommand = "server version`click here`/version";
String unfinishedCommand = "send a private message`complete this command`/tell `";
TablePrinter.printRaw((CommandSender) myReader, someTextAbove);
</i>

MULTILANGUAGE:

This class is (hopely) ready for: english, spanish, portuguese, french, german and italian. 
Most latin-ascii based languages may be supported (european?, russian? (not needed since they use unicode)). 
More info: https://en.wikipedia.org/wiki/List_of_languages_by_number_of_native_speakers. 
If there are some latin based characters that displays incorrectly just tell me. 

VERTICAL ALIGNED TEXT WITH ANY CHARACTER (ORIENTAL, ARABIC, SYMBOLS, ETC.)

If you feel motivated you could write a class that write column texts in ANY unicode character. 
Write a class (base on this if you want) that reads "glyph_sizes.bin" took from minecraft dir. 
First find its "obfuscated" name in <minecraft dir>/assets/indexes/1.8.json

The file is easy, the position in the file matches the unicode code points. 
From each byte in the file, the 4 bits msb and lsb are the left and right character boundaries. 
The difference beetween boundaries are the character width!, test it with MinecraftFontEditor. 
If you got interested and write some code please show me :D
</pre>
*/

public class TablePrinter
{
	//private Map<Integer, String> ch = new HashMap<Integer, String>();
	private static String[] ch = {
		"",
		"",
		"!.,:;i|\u00A1",
		"'`l\u00ED\u00CE",
		" I[]t\u00CD",
		"\"()*<>fk{}\u00AB\u00BB",
		"",
		"@~"};
	private static String[] sp = {
		"",
		"\u00A78\u205A",
		"\u00A78\u205A\u205A",
		"\u00A78\u205A\u205A\u205A",
		" ",
		"\u00A7L ",
		"\u00A78\u205A\u00A7L ",
		"\u00A78\u205A\u205A\u00A7L ",
		"  ",
		" \u00A7L ",
		"\u00A7L  ",
		"\u00A78\u205A\u00A7L  "};
	private String format;
	private int height;
	private List<Integer> tabs = new ArrayList<Integer>();
	private String[] lines;
	private int pages;
	private boolean forConsole;
	
	/**
	 * @param defaultFormat the format all fields will reset by default (see format codes)
	 * @param height default page height in lines according chat area (recommended 10 or less, max 20)
	 * @param tabs width of each field but last, negative means right aligned (considering 53 chars of 6px for each line)
	 */
	TablePrinter(String defaultFormat, int height, int... tabs)
	{
		format = defaultFormat;
		this.height = height;
		
		// init tabs
		int last = 53;
		for (int t : tabs)
		{
			this.tabs.add(t);
			last += t > 0?-t:t;
		}
		this.tabs.add(last);
	}
	
	// sort the lines ignoring format codes
	// now it lacks the ability to ignore hover and run propeties, left as reference
	/* void sortLines()
	{
		Arrays.sort(lines, new Comparator<String>()
		{
			@Override
			public int compare(String s1, String s2)
			{
				// sorting idea
				// sort until find \t or `
				// copy desired column in front, sort strings and delete front column
				return s1.replaceAll("\u00A7.", "").compareTo(s2.replaceAll("\u00A7.", ""));
			}
		});
	} */

	// get pages according specified height
	int countLines(String text)
	{
		return (int) Math.ceil((double) (StringUtils.countMatches(text, "\n") + 1) / height);
	}
	
	// convenient method
	void print(CommandSender sender, String text)
	{
		print(sender, text, 0);
	}
	
	// here the magic happens
	void print(CommandSender sender, String text, int page)
	{
		// previous tasks
		lines = text.split("[\r\n]+");
		pages = countLines(text);
		forConsole = !(sender instanceof Player);
		
		// define lines range
		int from = 0;
		int to = lines.length;
		if (page > 0)
		{
			from = (Math.min(page, pages) - 1) * height;
			to = Math.min(from + height, lines.length);
		}
		
		// initialize table, loop selected lines
		String line;
		for (int l = from; l < to; ++l)
		{
			// initialize line, loop fields (empty lines already skipped in text.split())
			line = "";
			String[] fields = lines[l].split("\t");
			for (int f = 0; f < fields.length; ++f)
			{
				// get field parameters
				String field = fields[f];
				int tab = tabs.get(f);
				
				// add formatted field to line if lefts
				if (f < tabs.size())
					line += formatField(field, tab, true);
				else
				{
					line += formatField(field, tab, tab < 0);
					break;
				}
			}
			if (forConsole)
				sender.sendMessage(line);
			else
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw "+sender.getName()+" [\"\""+line.replace("\\", "\\\\")+"]");
		}
	}
	
	// returns the given field, adjusted to tab width, formatted and aligned
	private String formatField(String field, int tab, boolean fill)
	{
		String text = getTextElement(field);
		boolean leftHand = true;
		if (tab < 0)
		{
			tab = -tab;
			leftHand = false;
		}

		if (!forConsole)
			tab *= 6;
		
		// trim text to avoid field overflow
		while (pxLen(text) > tab)
		{
			text = text.substring(0, text.length()-1);
			while (text.matches("\u00A7.$"))
				text = text.replaceAll("\u00A7.$", "");
		}
		
		// build fill space
		String blank = fill ? fillSpaces(tab - pxLen(text), leftHand) : "";
		
		// add format and spaces to text
		if (leftHand)
			//field = field.replaceFirst(getText(field), format+text+blank);
			text = format+text+blank;
		else
			text = blank+format+text;

		// return formatted for sendMessage()
		if (forConsole)
			return "\u00A7R"+text;
		
		// return formatted for /tellraw
		String[] attr = field.split("`");
		if (attr.length == 0)
			return "";
		String ans = ",{\"text\":\""+text+"\"";
		if (attr.length > 1)
			ans += ",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""+attr[1]+"\"}";
		if (attr.length > 2)
			ans += ",\"clickEvent\":{\"action\":\""+(field.endsWith("`")?"suggest":"run")+"_command\",\"value\":\""+attr[2]+"\"}";
		return ans+"}";
	}
	
	// return blank fill, part of the magic too
	private String fillSpaces(int len, boolean leftHand)
	{
		String blank = "";
		
		// fixed width
		if (forConsole)
			for (int i = 0; i < len; ++i)
				blank += " ";
		
		// variable width less than 12 pixels
		else if (len < 12)
		{
			blank = sp[len];
			
			// special right aligned thin spaces
			if (!leftHand)
			switch (len)
			{
			case 6:
				blank = "\u00A7L \u00A7R\u00A78\u205A";
				break;
			case 7:
				blank = "\u00A7L \u00A7R\u00A78\u205A\u205A";
				break;
			case 11:
				blank = "\u00A7L  \u00A7R\u00A78\u205A";
			}
		}
		
		// variable width from 12 pixels
		else
		{
			int px5 = len % 4;
			int px4 = (len / 4) - px5;
			
			for (int i = 0; i < px4; ++i)
				blank += " ";
			blank += "\u00A7L";
			for (int i = 0; i < px5; ++i)
				blank += " ";
		}
		
		return blank+"\u00A7R";
	}
	
	// calculate the length of a string in pixels, part of the magic
	private int pxLen(String field)
	{
		String stripped = field.replaceAll("\u00A7.", "");
		
		// if fixed width fonts, trim format codes and return fixed length
		if (forConsole)
			return stripped.length();

		// else loop word each character searching widths
		int len = 0;
		for (char c : stripped.toCharArray())
		{	
			// loop fixed characters list with default 6 pixels width
			int l = 6;
			for (int px = 1; px < 8; ++px)
				// add matched width to lenght
				if (ch[px].indexOf(c) >= 0)
				{
					l = px;
					break;
				}
			len += l;
		}
		// return the resulting lenght
		return len;
	}
	
	// field attributes management
	private String getTextElement(String field)
	{
		int index = field.indexOf("`");
		if (index == -1)
			return field;
		return field.substring(0, index);
	}
	
	/**
	 * prints text links on console (server or client chat)
	 * @param sender the message destination
	 * @param raw the actual message: "text`tooltip", "text`tooltip`command" or "text`tooltip`suggest`"
	 */
	static void printRaw(CommandSender sender, String raw)
	{
		String text;
		if (sender instanceof Player)
		{
			for (String l : raw.split("\n"))
			{
				text = "";
				String attr[];
				for (String field : l.split("\t"))
				{
					attr = field.split("`");
					if (attr.length == 0)
						continue;
					text += ",{\"text\":\""+attr[0]+"\"";
					if (attr.length > 1)
						text += ",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""+attr[1]+"\"}";
					if (attr.length > 2)
						text += ",\"clickEvent\":{\"action\":\""+(field.endsWith("`")?"suggest":"run")+"_command\",\"value\":\""+attr[2]+"\"}";
					text += "}";
				}
				Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw "+sender.getName()+" [\"\""+text.replace("\\", "\\\\")+"]");
			}
		}
		else
		{
			int index;
			for (String l : raw.split("\n"))
			{
				text = "";
				for (String field : l.split("\t"))
				{
					index = field.indexOf("`");
					if (index < 0)
						text += field;
					else
						text += field.substring(0, index);
				}
				sender.sendMessage(text);
			}
		}
	}
}
