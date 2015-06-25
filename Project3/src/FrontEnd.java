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
	
	private int portNumber = 6050;
	
	private HashMap<String, ArrayList<String>> cache = new HashMap<String, ArrayList<String>>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private HashMap<String, Integer>  timestamp = new HashMap<String, Integer>();
	private ReadWriteLock lock_timestamp = new ReentrantReadWriteLock();
	
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
	}
	
	public String getBackEndFromDiscover()
	{
		String result = new String();
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			String requestheaders = "GET /backend? HTTP/1.1\n";
			
			output.write(requestheaders);
			output.write("\n");
			output.flush();
			
			input.readLine();
			JSONObject jsonObject = parse(input.readLine());
			result = (String)jsonObject.get("ip_address");
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public JSONArray getLiveNodesFromDiscover()
	{
		JSONArray result = new JSONArray();
		try {
			Socket socket = new Socket(discover_hostname, discover_portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			String requestheaders = "GET /livenodes? HTTP/1.1\n";
			
			output.write(requestheaders);
			output.write("\n");
			output.flush();
			
			input.readLine();
			String value = input.readLine();
			try {
				result = (JSONArray) parser.parse(value);
		    } catch (ParseException pe) {
		      System.out.println("Error: could not parse JSON response:");
		      System.out.println(value);
		      System.exit(1);
		    }
			
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
			String backend = getBackEndFromDiscover();
			Socket socket = new Socket(backend, portNumber);
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
			
			String requestheaders = "POST /tweets HTTP/1.1\n";
			String requestbody = result.toString();
			
			output.write(requestheaders);
			output.write(requestbody);
			output.write("\n");
			output.flush();
			
			String header = input.readLine();
			
			JSONObject jsonObject = parse(input.readLine());
			HashMap<String, Integer> newTimestamp = parseTimestamp((JSONArray)jsonObject.get("timestamp"));
			setTimestamp(newTimestamp);
			
			return header;
			
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
			String backend = getBackEndFromDiscover();
			Socket socket = new Socket(backend, portNumber);
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			String requestheaders = "GET /tweets?" + "q=" + tag + " HTTP/1.1\n";
			JSONObject object = makeTimestamp();
			
			output.write(requestheaders);
			output.write(object.toString());
			output.write("\n");
			output.flush();
			
			String header = input.readLine();
			if (header.isEmpty())
			{
				String body = input.readLine();
				JSONObject jsonObject = parse(body);
				JSONObject timestamp = (JSONObject)jsonObject.get("timestamp");
				HashMap<String, Integer> newTimestamp = parseTimestamp((JSONArray)timestamp.get("timestamp"));
				setTimestamp(newTimestamp);
				
				result.put("q", tag);
				result.put("tweets", new JSONObject());
			}
			else if (header.equals("HTTP/1.1 200 OK"))
			{
				String body = input.readLine();
				JSONObject jsonObject = parse(body);
				
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
					cache.clear();
					cache.put(tag, temp);
					
					JSONObject timestamp = (JSONObject)jsonObject.get("timestamp");
					HashMap<String, Integer> newTimestamp = parseTimestamp((JSONArray)timestamp.get("timestamp"));
					setTimestamp(newTimestamp);
					
				}
				finally
				{
					lock.writeLock().unlock();
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
	
	public String getCounter()
	{
		int counter = 0;
		JSONArray array = getLiveNodesFromDiscover();
		ArrayList<String> nodes = new ArrayList<String>();
		for(int i = 0; i < array.size(); i++)
		{
			nodes.add((String)array.get(i));
		}
		
		for(int i = 0; i < nodes.size(); i++)
		{
			try {
				String backend = nodes.get(i);
				Socket socket = new Socket(backend, portNumber);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
						new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "GET /counter? HTTP/1.1\n";
				
				output.write(requestheaders);
				output.write("\n");
				output.flush();
				
				input.readLine();
				String value = input.readLine();
				counter += Integer.parseInt(value);
				
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return Integer.toString(counter);
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
	
	public void setTimestamp(HashMap<String, Integer> newTimestamp) {
		try {
			lock_timestamp.writeLock().lock();
		
			timestamp.clear();
			timestamp = newTimestamp;
		}
		finally
		{
			lock_timestamp.writeLock().unlock();
		}
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
	
	private synchronized JSONObject parse(String s)
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
