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
import java.util.TreeMap;
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
	private int    id;
	
	private String primary = null;
	private ReadWriteLock lock_primary = new ReentrantReadWriteLock();
	
	private HashMap<String, ArrayList<String>> hashmap = new HashMap<String, ArrayList<String>>();
	private static HashMap<String, Integer>  tag_version = new HashMap<String, Integer>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private int timestamp = 0;
	private ReadWriteLock lock_counter = new ReentrantReadWriteLock();
	private TreeMap<Integer, String> log = new TreeMap<Integer, String>();
	private ReadWriteLock lock_log = new ReentrantReadWriteLock();
	
	private Heartbeat heartbeat;
	private final int steps = 5;

	public void addHeartbeat(Heartbeat heart)
	{
		heartbeat = heart;
	}
	
	public void getLikeMe(String requests)
	{
		JSONObject object = parse(requests);
	    
	    setPrimary((String)object.get("primary"));
	    int my_version = getClock_counter();
	    int new_leader_version = Integer.parseInt((String)object.get("maxVersion"));
	    if (my_version < new_leader_version)
	    {
		    JSONArray array = (JSONArray)object.get("write");
			for (int i = 0; i < array.size(); i++)
			{
				JSONObject jsonObject = (JSONObject)array.get(i);
				int version = Integer.parseInt((String)jsonObject.get("version"));
				String request = (String)jsonObject.get("request");
				if (!getLog().containsKey(version))
				{
					write(request);
				}
			}
	    }
	    else if (my_version > new_leader_version)
	    {
	    	for (int i = 0; i < my_version - new_leader_version; i++)
	    	{
	    		rollback(getLog().get(my_version - i));
	    	}
	    }
	}
	
	@SuppressWarnings("unchecked")
	public void broadcastAsNewLeader(ArrayList<String> secondaries)
	{
		for (int i = 0; i < secondaries.size(); i++)
		{
			try
			{
				Socket socket = new Socket(secondaries.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				JSONObject object = new JSONObject();
				JSONArray array = new JSONArray();
				
				int my_version = getClock_counter();
				try {
					lock_log.readLock().lock();
					
					for (int j = 0; j < steps; j++)
					{
						JSONObject jsonObject = new JSONObject();
						int current = my_version - j;
						jsonObject.put(Integer.toString(current), log.get(current));
						array.add(jsonObject);
					}
				}
				finally
				{
					lock_log.readLock().unlock();
				}
				
				int version = getClock_counter();

				object.put("primary", ip_address);
				object.put("write", array);
				object.put("maxVersion", Integer.toString(version));
				
				String requestheaders = "POST /getLikeMe HTTP/1.1\n";
				String requestbody = object.toString();
				
				output.write(requestheaders);
				output.write(requestbody);
				output.write("\n");
				output.flush();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public void election()
	{
		setPrimaryAsNull();
		ArrayList<String> livenodes = new ArrayList<String>();
		ArrayList<String> highernodes = new ArrayList<String>();
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
		
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
				new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			JSONObject object = new JSONObject();
			object.put("ip_address", ip_address);
			
			String requestheaders = "GET /livenodes? HTTP/1.1\n";
			String requestbody = object.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			input.readLine();
			String response = input.readLine();
			JSONArray array = null;
		    try {
		    	array = (JSONArray) parser.parse(response);
		    } catch (ParseException pe) {
		      System.out.println("Error: could not parse JSON response:");
		      System.out.println(response);
		      System.exit(1);
		    }
		    
		    for(int i = 0; i < array.size(); i++)
		    {
		    	JSONObject servers = (JSONObject)array.get(i);
		    	String secondary = (String)servers.get("ip_address");
		    	livenodes.add(secondary);
		    	if (Integer.parseInt((String)servers.get("id")) > id)
		    	{
		    		highernodes.add(secondary);
		    	}
		    }
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
			
		int response_num = 0;
		try
		{
			for (int i = 0; i < highernodes.size(); i++)
			{
				Socket socket = new Socket(highernodes.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "GET /alive? HTTP/1.1\n";
				JSONObject object = new JSONObject();
				object.put("my_ip_address", ip_address);
				String requestbody = object.toString();
				
				output.write(requestheaders);
				output.write(requestbody);
				output.write("\n");
				output.flush();
				
				input.readLine();
				if (input.readLine().equals("alive"))
				{
					response_num += 1;
				}
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (response_num == 0)
		{
			setPrimary(ip_address);
			broadcastAsNewLeader(livenodes);
			updateDiscover(livenodes);
		}
		heartbeat.reset();
	}

	@SuppressWarnings("unchecked")
	public void updateDiscover(ArrayList<String> secondaries)
	{
		Socket socket;
		try {
			socket = new Socket(discover_hostname, discover_portNumber);
		
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
				new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			JSONObject object = new JSONObject();
			JSONArray array = new JSONArray();
			array.add(ip_address);

			for(int i = 0; i < secondaries.size(); i++)
			{
				array.add(secondaries.get(i));
			}
			
			object.put("primary", getPrimary());
			object.put("servers", array);
			
			String requestheaders = "POST /primary HTTP/1.1\n";
			String requestbody = object.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public void broadcastWrite(String request)
	{

		ArrayList<String> livenodes = new ArrayList<String>();
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
		
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
				new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			JSONObject result = new JSONObject();
			result.put("ip_address", ip_address);
			
			String requestheaders = "GET /livenodes? HTTP/1.1\n";
			String requestbody = result.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			input.readLine();
			String response = input.readLine();
			JSONArray array = null;
		    try {
		    	array = (JSONArray) parser.parse(response);
		    } catch (ParseException pe) {
		      System.out.println("Error: could not parse JSON response:");
		      System.out.println(response);
		      System.exit(1);
		    }
		    
		    for(int i = 0; i < array.size(); i++)
		    {
		    	JSONObject servers = (JSONObject)array.get(i);
		    	livenodes.add((String)servers.get("ip_address"));
		    }
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		for (int i = 0; i < livenodes.size(); i++)
		{
			try
			{
				Socket socket = new Socket(livenodes.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "POST /tweets HTTP/1.1\n";
				
				output.write(requestheaders);
				output.write(request);
				output.write("\n");
				output.flush();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	// main three functionality in high level
	public void write(String request)
	{
		if (getPrimary() == null) return;
		try {
			lock.writeLock().lock();
			
			try {
				lock_log.writeLock().lock();
				increaseClock_counter();
				log.put(getClock_counter(), request);
			}
			finally
			{
				lock_log.writeLock().unlock();
			}
			
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
	
	public void rollback(String request)
	{
		try {
			lock.writeLock().lock();
			
			JSONObject object = parse(request);
		    
			String tweet = (String)object.get("tweet");
			JSONArray hashtags = (JSONArray)object.get("hashtags");
			for (int i = 0; i < hashtags.size(); i++)
			{
				String hashtag = (String)hashtags.get(i);
				remove(hashtag, tweet);
			}
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject read(String tag, String version)
	{
		try {
			lock.readLock().lock();
			
			JSONObject result = new JSONObject();
			
			result.put("q", tag);
			
			JSONArray tweets = search(tag, version);
			result.put("tweets", tweets);
			
			if (tag_version.containsKey(tag))
			{
				String v = Integer.toString(tag_version.get(tag));
				result.put("v", v);
			}
			else
			{
				result.put("v", version);
			}
			return result;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}
	
	// three sub functionality
	private void remove(String tag, String tweet)
	{
		if (hashmap.containsKey(tag))
		{
			hashmap.get(tag).remove(tweet);
		}
		
		if (tag_version.containsKey(tag))
		{
			int previous_version = tag_version.get(tag);
			int current_version = previous_version - 1;
			tag_version.put(tag, current_version);
		}
	}
	
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
		
		if (!tag_version.containsKey(tag))
		{
			tag_version.put(tag, 1);
		}
		else
		{
			int previous_version = tag_version.get(tag);
			int current_version = previous_version + 1;
			tag_version.put(tag, current_version);
		}
	}
	
	@SuppressWarnings("unchecked")
	private JSONArray search(String tag, String v)
	{	
		int version = Integer.parseInt(v);
		JSONArray array = new JSONArray();
		if (tag_version.containsKey(tag) && tag_version.get(tag) > version)
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
			
			String requestheaders = "POST /register HTTP/1.1\n";
			String requestbody = object.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			input.readLine();
			jsonObject = parse(input.readLine());
			primary = (String) jsonObject.get("primary");
			id = Integer.parseInt((String) jsonObject.get("id"));
			
			if (!primary.equals(ip_address))
			{
				catchup();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void catchup()
	{
		try {
			Socket socket = new Socket(primary, 6050);
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
			JSONArray object = new JSONArray();
			
			try {
				object = (JSONArray) parser.parse(jsonString);
			} catch (ParseException pe) {
		      System.out.println("Error: could not parse JSON response:");
		      System.out.println(jsonString);
		      System.exit(1);
			}
			
			replicateFrom(object);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	
	public void setPrimaryAsNull() {
		try {
			lock_primary.writeLock().lock();
			
			this.primary = null;
		}
		finally
		{
			lock_primary.writeLock().unlock();
		}
	}
	
	public int getClock_counter() {
		try {
			lock_counter.readLock().lock();
		
			return timestamp;
		}
		finally
		{
			lock_counter.readLock().unlock();
		}
	}

	public void increaseClock_counter() {
		try {
			lock_counter.writeLock().lock();
			
			this.timestamp += 1;
		}
		finally
		{
			lock_counter.writeLock().unlock();
		}
	}
	
	@SuppressWarnings("unchecked")
	public JSONArray replicateTo()
	{
		JSONArray array = new JSONArray();
		
		try {
			lock_log.readLock().lock();
			for(int key : log.keySet())
			{
				array.add(log.get(key));
			}
		}
		finally
		{
			lock_log.readLock().unlock();
		}
		
		return array;
	}
	
	public void replicateFrom(JSONArray array)
	{
		for(int i = 0; i < array.size(); i++)
		{
			write((String)array.get(i));
		}
	}
	
	public TreeMap<Integer, String> getLog()
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
	
	public String getIp_address() {
		return ip_address;
	}

	public void setIp_address(String ip_address) {
		this.ip_address = ip_address;
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
