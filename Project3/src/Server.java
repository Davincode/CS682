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


public class Server {

	private static final String filePath = "config.json";
	public  JSONParser parser = new JSONParser();
	private String discover_hostname;
	private int discover_portNumber;
	
	private String ip_address;
	private ArrayList<String> servers = new ArrayList<String>();
	private ReadWriteLock lock_servers = new ReentrantReadWriteLock();
	
	private HashMap<String, ArrayList<String>> hashmap = new HashMap<String, ArrayList<String>>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private HashMap<String, Integer> timestamp = new HashMap<String, Integer>();
	private ReadWriteLock lock_timestamp = new ReentrantReadWriteLock();
	
	private HashMap<JSONObject, String> log = new HashMap<JSONObject, String>();
	private ReadWriteLock lock_log = new ReentrantReadWriteLock();

	@SuppressWarnings("unchecked")
	public void broadcastWrite(String request)
	{
		ArrayList<String> servers = getServers();
	    for(int i = 0; i < servers.size(); i++)
	    {	
	    	String current = null;
			try {
		    	current = servers.get(i);
		    	Socket socket = new Socket(servers.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "POST /broadcastTweets HTTP/1.1\n";
				
				JSONObject object = new JSONObject();
	
				object.put("ip_address", ip_address);
				object.put("timestamp", makeTimestamp());
				object.put("value", request);
				
				output.write(requestheaders);
				output.write(object.toString());
				output.write("\n");
				output.flush();
				
				Thread.sleep(1000);
			    
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
				if(current != null)
				{
					removeServer(current);
					replicateSomeFromOtherServers(makeTimestamp(), current);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
	}
	
	public void writeFromBroadcast(String request)
	{
		JSONObject object = parse(request);
		String value = (String)object.get("value");
		JSONObject timestamp_object = (JSONObject)object.get("timestamp");
		HashMap<String, Integer> timestamp = parseTimestamp((JSONArray)timestamp_object.get("timestamp"));
		if (mergeTimestamp(timestamp))
		{
			writeFrom(value, timestamp);
		}
	}
	
	// main functionality in high level
	public void write(String request)
	{
		try {
			lock_log.writeLock().lock();
			increaseTimestamp();
			log.put(makeTimestamp(), request);
		}
		finally
		{
			lock_log.writeLock().unlock();
		}
		
		try {
			lock.writeLock().lock();
			JSONObject object = parse(request);
		    
			String tweet = (String)object.get("tweet");
			JSONArray hashtags = (JSONArray)object.get("hashtags");
			for (int i = 0; i < hashtags.size(); i++)
			{
				String hashtag = (String)hashtags.get(i);
				insert(hashtag, tweet);
			}	
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}
	
	public void writeFrom(String request, HashMap<String, Integer> timestamp)
	{
		try {
			lock_log.writeLock().lock();
			
			log.put(makeTimestamp(timestamp), request);
		}
		finally
		{
			lock_log.writeLock().unlock();
		}
		
		try {
			lock.writeLock().lock();
			JSONObject object = parse(request);
		    
			String tweet = (String)object.get("tweet");
			JSONArray hashtags = (JSONArray)object.get("hashtags");
			for (int i = 0; i < hashtags.size(); i++)
			{
				String hashtag = (String)hashtags.get(i);
				insert(hashtag, tweet);
			}	
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject read(String tag, String request)
	{
		try {
			lock.readLock().lock();
			
			JSONObject object = parse(request);
			
			JSONObject result = new JSONObject();
			
			result.put("q", tag);
			
			boolean flag = true;
			HashMap<String, Integer> current = getTimestamp();
			
			HashMap<String, Integer> timestamp = parseTimestamp((JSONArray)object.get("timestamp"));
			
			for(String ip : timestamp.keySet())
			{
				if(!current.containsKey(ip) || current.get(ip) < timestamp.get(ip))
				{
					flag = false;
				}
			}
			
			if(flag)
			{
				JSONArray tweets = search(tag);
				result.put("tweets", tweets);			
				result.put("timestamp", makeTimestamp(current));
				result.put("isUpdate", "Y");
			}
			else
			{
				result.put("isUpdate", "W");
				result.put("timestamp", makeTimestamp(current));
			}

			return result;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}
	
	// sub functionality	
	private void insert(String tag, String tweet)
	{
		if (!hashmap.containsKey(tag))
		{
			ArrayList<String> tweets = new ArrayList<String>();
			tweets.add(tweet);
			hashmap.put(tag, tweets);
		}
		else
		{
			ArrayList<String> t = hashmap.get(tag);
			t.add(tweet);
			hashmap.put(tag, t);
		}
	}
	
	@SuppressWarnings("unchecked")
	private JSONArray search(String tag)
	{	
		JSONArray array = new JSONArray();
		if (hashmap.containsKey(tag))
		{
			ArrayList<String> result = hashmap.get(tag);
			for (int i = 0; i < result.size(); i++)
			{
				array.add(result.get(i));
			}
		}
		return array;
	}
	
	@SuppressWarnings("unchecked")
	public Server()
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
		
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			JSONObject object = new JSONObject();
			ip_address = socket.getLocalAddress().getHostAddress();
			object.put("ip_address", ip_address);
			
			String requestheaders = "POST /registerBackEnd HTTP/1.1\n";
			String requestbody = object.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			input.readLine();
			jsonObject = parse(input.readLine());
			
			JSONArray array = (JSONArray)jsonObject.get("servers");
			for (int i = 0; i < array.size(); i++)
			{
				servers.add((String)array.get(i));
			}
			
			if(servers.size() != 0)
			{
				JSONObject timestamp = catchup(servers.get(0));
				registerToOtherServers(timestamp);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public JSONArray register(String request)
	{
		JSONObject object = parse(request);
		// the only thing differ from replicate
		addServer((String)object.get("ip_address"));

		JSONObject timestamp_object = (JSONObject)object.get("timestamp");
		HashMap<String, Integer> timestamp = parseTimestamp((JSONArray)timestamp_object.get("timestamp"));
		
		JSONArray result = new JSONArray();
		HashMap<JSONObject, String> log = getLog();
		for(JSONObject key : log.keySet())
		{
			if(isOlder(timestamp, key))
			{
				JSONObject elem = new JSONObject();
				elem.put("key", key);
				elem.put("value", log.get(key));
				result.add(elem);
			}
		}
		return result;
	}
	
	public void unregister(String request)
	{
		JSONObject object = parse(request);
		removeServer((String)object.get("ip_address"));
	}
	
	@SuppressWarnings("unchecked")
	public JSONArray replicate(String request)
	{
		JSONObject object = parse(request);
		
		JSONObject timestamp_object = (JSONObject)object.get("timestamp");
		HashMap<String, Integer> timestamp = parseTimestamp((JSONArray)timestamp_object.get("timestamp"));
		
		JSONArray result = new JSONArray();
		HashMap<JSONObject, String> log = getLog();
		for(JSONObject key : log.keySet())
		{
			if(isOlder(timestamp, key))
			{
				JSONObject elem = new JSONObject();
				elem.put("key", key);
				elem.put("value", log.get(key));
				result.add(elem);
			}
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public void replicateSomeFromOtherServers(JSONObject timestamp, String ip_address)
	{
		String current = null;
		try {
			ArrayList<String> servers = getServers();
		    for(int i = 0; i < servers.size(); i++)
		    {	
		    	current = servers.get(i);
		    	Socket socket = new Socket(servers.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				// the only thing differ from register to other servers
				String requestheaders = "GET /replicateSome HTTP/1.1\n";
				
				JSONObject object = new JSONObject();
				object.put("ip_address", ip_address);
				object.put("timestamp", timestamp);
				
				output.write(requestheaders);
				output.write(object.toString());
				output.write("\n");
				output.flush();
				
				input.readLine();
				String value = input.readLine();
				JSONArray array = null;
				try {
					array = (JSONArray) parser.parse(value);
			    } catch (ParseException pe) {
			      System.out.println("Error: could not parse JSON response:");
			      System.out.println(value);
			      System.exit(1);
			    }
				
				replicateFrom(array);
		    }
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			if(current != null)
			{
				removeServer(current);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void registerToOtherServers(JSONObject timestamp)
	{
		String current = null;
		try {
			ArrayList<String> servers = getServers();
		    for(int i = 0; i < servers.size(); i++)
		    {	
		    	current = servers.get(i);
		    	Socket socket = new Socket(servers.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "POST /register HTTP/1.1\n";
				
				JSONObject object = new JSONObject();
				object.put("ip_address", ip_address);
				object.put("timestamp", timestamp);
				
				output.write(requestheaders);
				output.write(object.toString());
				output.write("\n");
				output.flush();
				
				input.readLine();
				String value = input.readLine();
				JSONArray array = null;
				try {
					array = (JSONArray) parser.parse(value);
			    } catch (ParseException pe) {
			      System.out.println("Error: could not parse JSON response:");
			      System.out.println(value);
			      System.exit(1);
			    }
				
				replicateFrom(array);
		    }
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			if(current != null)
			{
				removeServer(current);
			}
		}
	}
	
	public JSONObject catchup(String ip_address)
	{
		JSONObject result = new JSONObject();
		try {
			Socket socket = new Socket(ip_address, 6050);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			String requestheaders = "GET /replicateAll HTTP/1.1\n";
			output.write(requestheaders);
			output.write("\n");
			output.flush();
			
			input.readLine();
			String jsonString = input.readLine();
			JSONObject object = parse(jsonString);
			
			replicateFrom((JSONArray)object.get("log"));
			result = (JSONObject)object.get("timestamp");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public HashMap<String, Integer> getTimestamp() {
		try {
			lock_timestamp.readLock().lock();
		
			return timestamp;
		}
		finally
		{
			lock_timestamp.readLock().unlock();
		}
	}

	public void increaseTimestamp() {
		try {
			lock_timestamp.writeLock().lock();
			
			if(timestamp.containsKey(ip_address))
			{
				int version = timestamp.get(ip_address);
				int current = version + 1;
				timestamp.remove(ip_address);
				timestamp.put(ip_address, current);
			}
			else
			{
				timestamp.put(ip_address, 1);
			}
		}
		finally
		{
			lock_timestamp.writeLock().unlock();
		}
	}
	
	public boolean mergeTimestamp(HashMap<String, Integer> timestamp) {
		
		try {
			boolean result = false;
			lock_timestamp.writeLock().lock();
			
			HashMap<String, Integer> my_timestamp = getTimestamp();
			for(String ip_address : timestamp.keySet())
			{
				if(my_timestamp.containsKey(ip_address))
				{
					if(my_timestamp.get(ip_address) < timestamp.get(ip_address))
					{
						my_timestamp.remove(ip_address);
						my_timestamp.put(ip_address, timestamp.get(ip_address));
						result = true;
					}
					
					if(my_timestamp.get(ip_address) != timestamp.get(ip_address))
					{
						result = true;
					}
				}
				else
				{
					my_timestamp.put(ip_address, timestamp.get(ip_address));
					result = true;
				}
			}
			return result;
		}
		finally
		{
			lock_timestamp.writeLock().unlock();
		}
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject replicateTo()
	{
		JSONObject result = new JSONObject();
		HashMap<JSONObject, String> log = getLog();
		
		JSONArray array = new JSONArray();
		for(JSONObject key : log.keySet())
		{
			JSONObject object = new JSONObject();
			object.put("key", key);
			object.put("value", log.get(key));
			array.add(object);
		}
		result.put("log", array);
		result.put("timestamp", makeTimestamp());
		return result;
	}
	
	public void replicateFrom(JSONArray array)
	{
		for(int i = 0; i < array.size(); i++)
		{
			JSONObject object = (JSONObject)array.get(i);
			
			JSONObject timestamp_object = (JSONObject)object.get("key");
			HashMap<String, Integer> timestamp = parseTimestamp((JSONArray)timestamp_object.get("timestamp"));
			if (mergeTimestamp(timestamp))
			{
				writeFrom((String)object.get("value"), timestamp);
			}
		}
	}
	
	public HashMap<JSONObject, String> getLog()
	{
		try {
			lock_log.readLock().lock();

			return log;
		}
		finally
		{
			lock_log.readLock().unlock();
		}
	}
	
	public void addServer(String ip_address)
	{
		try {
			lock_servers.writeLock().lock();

			servers.add(ip_address);
		}
		finally
		{
			lock_servers.writeLock().unlock();
		}
	}
	
	public void removeServer(String ip_address)
	{
		try {
			lock_servers.writeLock().lock();

			servers.remove(ip_address);
		}
		finally
		{
			lock_servers.writeLock().unlock();
		}
	}
	
	public ArrayList<String> getServers()
	{
		try {
			lock_servers.readLock().lock();

			return servers;
		}
		finally
		{
			lock_servers.readLock().unlock();
		}
	}
	
	public String getIp_address() {
		return ip_address;
	}

	public void setIp_address(String ip_address) {
		this.ip_address = ip_address;
	}
	
	public HashMap<String, Integer> parseTimestamp(JSONArray array)
	{
		HashMap<String, Integer> result = new HashMap<String, Integer>();
		for(int i = 0; i < array.size(); i++)
		{
			JSONObject ts = (JSONObject)array.get(i);
			result.put((String)ts.get("ip_address"), Integer.parseInt((String)ts.get("version")));
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject makeTimestamp()
	{
		HashMap<String, Integer> timestamp = getTimestamp();
		
		JSONObject object = new JSONObject();
		JSONArray array = new JSONArray();
		for(String key : timestamp.keySet())
		{
			JSONObject map = new JSONObject();
			map.put("ip_address", key);
			map.put("version", Integer.toString(timestamp.get(key)));
			array.add(map);
		}
		object.put("timestamp", array);
		return object;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject makeTimestamp(HashMap<String, Integer> timestamp)
	{	
		JSONObject object = new JSONObject();
		JSONArray array = new JSONArray();
		for(String key : timestamp.keySet())
		{
			JSONObject map = new JSONObject();
			map.put("ip_address", key);
			map.put("version", Integer.toString(timestamp.get(key)));
			array.add(map);
		}
		object.put("timestamp", array);
		return object;
	}
	
	public boolean isOlder(HashMap<String, Integer> a, JSONObject object)
	{
		HashMap<String, Integer> b = parseTimestamp((JSONArray)object.get("timestamp"));
		boolean result = true;
		boolean isEqual = true;
		for(String key : a.keySet())
		{
			if(!b.containsKey(key))
			{
				isEqual = false;
			}
			else if(a.get(key) != b.get(key))
			{
				isEqual = false;
			}
			
			if(!b.containsKey(key) || a.get(key) > b.get(key))
			{
				result = false;
			}
		}
		return result && !isEqual;
	}
	
	private JSONObject parse(String s)
	{
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) parser.parse(s);
	    } catch (ParseException pe) {
	      System.out.println("Error: could not parse JSON response:");
	      System.out.println(s);
	      System.exit(1);
	    }
		return jsonObject;
	}
	
}
