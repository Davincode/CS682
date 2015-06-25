import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class FrontEnd {

	private static final String filePath = "config.json";
	public  JSONParser parser = new JSONParser();
	private String discover_hostname;
	private int discover_portNumber;
	
	private String ip_address;
	private String primary;
	private int    portNumber = 6050;
	private ReadWriteLock lock_primary = new ReentrantReadWriteLock();
	
	private HashMap<String, ArrayList<String>> cache = new HashMap<String, ArrayList<String>>();
	private HashMap<String, Integer>  tag_version = new HashMap<String, Integer>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	public FrontEnd()
	{
		FileReader reader = null;
		try {
			reader = new FileReader(filePath);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) parser.parse(reader);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		discover_hostname = (String) jsonObject.get("ip");
		discover_portNumber = Integer.parseInt((String) jsonObject.get("port"));
		
		register();
		setPrimary(getPrimaryFromDiscover());
	}
	
	@SuppressWarnings("unchecked")
	public void register()
	{
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			ip_address = socket.getLocalAddress().getHostAddress();
			JSONObject object = new JSONObject();
			object.put("ip_address", ip_address);
			String requestheaders = "POST /registerFrontEnd HTTP/1.1\n";
			String requestbody = object.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			input.readLine();
			input.readLine();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public String getPrimaryFromDiscover()
	{
		String result = new String();
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			String requestheaders = "GET /primary? HTTP/1.1\n";
			
			output.write(requestheaders);
			output.write("\n");
			output.flush();
			
			input.readLine();
			JSONObject jsonObject = null;
			try {
				jsonObject = (JSONObject) parser.parse(input.readLine());
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
			
			result = (String)jsonObject.get("primary");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public String add(JSONObject object)
	{
		try {
			Socket socket = new Socket(getPrimary(), portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			String tweet = (String)object.get("text");
			String[] tokens = tweet.split(" ");
			JSONArray array = new JSONArray();
			
			boolean no_hashtag = true;
			for(String token : tokens)
			{
				if(token.startsWith("#"))
				{
					no_hashtag = false;
					array.add(token.substring(1));
				}
			}
			if (no_hashtag) return "no hashtag";
			
			JSONObject result = new JSONObject();
			result.put("tweet", object.get("text"));
			result.put("hashtags", array);
			String requestbody = result.toString();
			
			String requestheaders = "POST /tweets HTTP/1.1\n";
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			input.readLine();
			String body = input.readLine();
			return body;
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
		return "";
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject query(String tag)
	{
		JSONObject result = new JSONObject();
		try {
			Socket socket = new Socket(getPrimary(), portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			int version;
			try {
				lock.readLock().lock();
				if (tag_version.containsKey(tag))
				{
					version = tag_version.get(tag);
				}
				else
				{
					version = 0;
				}
			}
			finally
			{
				lock.readLock().unlock();
			}
			
			String requestheaders = "GET /tweets?" + "q=" + tag + "&v=" + Integer.toString(version) + " HTTP/1.1\n";
			
			output.write(requestheaders);
			output.flush();
			
			String header = input.readLine();
			if (header.equals("HTTP/1.1 200 OK"))
			{
				String body = input.readLine();
				JSONObject jsonObject = null;
				try {
					jsonObject = (JSONObject) parser.parse(body);
				} catch (ParseException e) {
					e.printStackTrace();
				}
				
				result.put("q", tag);
				result.put("tweets", jsonObject.get("tweets"));
				
				ArrayList<String> temp = new ArrayList<String>();
				JSONArray tweets = (JSONArray)jsonObject.get("tweets");
				for (int i = 0; i < tweets.size(); i++)
				{
					temp.add((String)tweets.get(i));
				}
				
				try {
					lock.writeLock().lock();
					cache.put(tag, temp);
					String v = (String)jsonObject.get("v");
					tag_version.put(tag, Integer.parseInt(v));
				}
				finally
				{
					lock.writeLock().unlock();
				}
			}
			else
			{
				result.put("q", tag);
				try {
					lock.readLock().lock();
					result.put("tweets", cache.get(tag));
				}
				finally
				{
					lock.readLock().unlock();
				}
			}
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			result.clear();
		}
		return result;
	}
	
	public String getPrimary() {
		try {
			lock_primary.readLock().lock();
		
			return primary;
		}
		finally
		{
			lock_primary.readLock().unlock();
		}
	}

	public void setPrimary(String primary) {
		try {
			lock_primary.writeLock().lock();
			
			this.primary = primary;
		}
		finally
		{
			lock_primary.writeLock().unlock();
		}
	}
	
}
