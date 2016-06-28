package cl.netgamer.endermail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public class DataBase
{
	private String path;
	private String user;
	private String pass;
	
	public DataBase(Main main)
	{
		// host, port, base, user, pass... auth, name <- authme table and username column
		FileConfiguration conf = main.getConfig();
		path =	"jdbc:mysql://"+conf.getString("DataSource.mySQLHost")+":"+conf.getInt("DataSource.mySQLPort")+"/"+conf.getString("DataSource.mySQLDatabase");
		user = conf.getString("DataSource.mySQLUsername");
		pass = conf.getString("DataSource.mySQLPassword");
		
		// test connection
		// create table if not exists
		Statement sta = newStatement();
		//ResultSet res = getResult("CREATE TABLE IF NOT EXISTS...", sta);
		ResultSet res = getResult("SELECT 'remember create table';", sta);
		if (res == null)
		{
			main.getLogger().severe("Can't connect to AuthMe database, check server and parameters. Exiting...");
			Bukkit.getPluginManager().disablePlugin(main);
		}
		closeStatement(sta);
	}
	
	Statement newStatement()
	{
		try
		{
			Class.forName("com.mysql.jdbc.Driver");
			return DriverManager.getConnection(path, user, pass).createStatement();
		}
		catch (ClassNotFoundException | SQLException e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	void closeStatement(Statement statement)
	{
		try
		{
			Connection connection = statement.getConnection();
			statement.close();
			connection.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}

	/**<pre>
	 * // usage:
	 * Statement myStatement = newStatement();
	 * ResultSet myResult = getResult("MY_SQL_QUERY;", myStatement);
	 * // do something with myResult
	 * closeStatement(myStatement);
	 * </pre>
	 * @param query the input sql query, terminated with semicolon
	 * @param statement where to run the queries
	 * @return the resulting ResultSet, last insert row id if query was not a "select", null in case of error
	 */
	ResultSet getResult(String query, Statement statement)
	{
		//System.out.println("QUERY = "+query);
		
		ResultSet result = null;
		try
		{
			if (query.toUpperCase().trim().startsWith("SELECT "))
				result = statement.executeQuery(query);
			else
				statement.executeUpdate(query);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * get 1st column 1st row from given query as int (useful with COUNT(*) and similar statements),<br/>
	 * with null Statement it will create a new temporary one and closes it before return,<br/>
	 * can also be used with INSERT statements in which case returns last inserted auto increment row id:
	 * <pre>
	 * Statement myStatement = newStatement();
	 * int myNumber = getInt("MY_SQL_QUERY();", myStatement);
	 * closeStatement(myStatement);
	 * myNumber.doSomething();
	 * </pre>
	 * @param query the input sql query, terminated with semicolon
	 * @param statement where to run the queries, or null to create one temporarily
	 * @return the resulting int or 0 in case of error
	 */
	int getInt(String query, Statement statement)
	{
		boolean once = statement == null;
		if (once)
			statement = newStatement();
		ResultSet result = getResult(query, statement);
		if (query.toUpperCase().trim().startsWith("INSERT "))
			result = getResult("SELECT LAST_INSERT_ID();", statement);
		
		int data = 0;
		try
		{
			if (result.next())
				data = result.getInt(1);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		if (once)
			closeStatement(statement);
		return data;
	}
	
	/**
	 * get 1st column 1st row from given query as String (i.e.: a single name or property),<br/>
	 * with null Statement it will create a new temporary one and closes it before return,<br/>
	 * <pre>
	 * Statement myStatement = newStatement();
	 * String myPhrase = getString("MY_SQL_QUERY();", myStatement);
	 * closeStatement(myStatement);
	 * myPhrase.doSomething();
	 * </pre>
	 * @param query the input sql query, terminated with semicolon
	 * @param statement where to run the queries, or null to create one temporarily
	 * @return the resulting String or an empty one in case of error
	 */
	String getString(String query, Statement statement)
	{
		boolean once = statement == null;
		if (once)
			statement = newStatement();
		ResultSet result = getResult(query, statement);
		
		String data = "";
		try
		{
			if (result.next())
				data = result.getString(1);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		if (once)
			closeStatement(statement);
		return data;
	}
	
}
