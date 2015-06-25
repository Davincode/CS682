import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class Discover {

	// maps ip_address to id
	private HashMap<String, String> nodes = new HashMap<String, String>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private String primary = new String();
	private ReadWriteLock lock_primary = new ReentrantReadWriteLock();
	
	private ArrayList<String> frontEnds = new ArrayList<String>();
	private ReadWriteLock lock_frontEnds = new ReentrantReadWriteLock();
	
	public Discover()
	{
		
	}
	
	@SuppressWarnings("unchecked")
	public void informFrontEnd()
	{
		ArrayList<String> frontEnds = getFrontEnds();
		for(int i = 0; i < frontEnds.size(); i++)
		{
			try {
				Socket socket = new Socket(frontEnds.get(i), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
						new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				JSONObject object = new JSONObject();
				object.put("primary", getPrimary());
				
				String requestheaders = "POST /primary HTTP/1.1\n";
				String requestbody = object.toString();
				
				output.write(requestheaders);
				output.write(requestbody);
				output.write("\n");
				output.flush();
				
				input.readLine();
				input.readLine();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int register(String ip_address)
	{
		try {
			lock.writeLock().lock();
			
			int id = nodes.size();
			if (id == 0)
			{
				setPrimary(ip_address);
			}
			nodes.put(ip_address, Integer.toString(id));
			return id;
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}
	
	public void unregister(String ip_address)
	{
		try {
			lock.writeLock().lock();
			
			nodes.remove(ip_address);
			if (ip_address.equals(primary))
			{
				setPrimary("");
			}
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}
	
	@SuppressWarnings("unchecked")
	public JSONArray getLiveNodes(String ip_address)
	{
		try {
			lock.readLock().lock();
			
			JSONArray result = new JSONArray();
			for(String key : nodes.keySet())
			{
				if (!key.equals(ip_address))
				{
					JSONObject object = new JSONObject();
					object.put("ip_address", key);
					object.put("id", nodes.get(key));
					result.add(object);
				}
			}
			return result;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}
	
	public void setLiveNodes(JSONArray servers)
	{
		ArrayList<String> livenodes = new ArrayList<String>();
		for (int i = 0; i < servers.size(); i++)
		{
			String ip_address = (String) servers.get(i);
			livenodes.add(ip_address);
		}
		
		try {
			lock.writeLock().lock();
			
			for(String key : nodes.keySet())
			{
				if (!livenodes.contains(key))
				{
					nodes.remove(key);
				}
			}
		}
		finally
		{
			lock.writeLock().unlock();
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
	
	public void addFrontEnd(String ip_address)
	{
		try {
			lock_frontEnds.writeLock().lock();
			frontEnds.add(ip_address);
		}
		finally
		{
			lock_frontEnds.writeLock().unlock();
		}
	}
	
	public ArrayList<String> getFrontEnds()
	{
		try {
			lock_frontEnds.readLock().lock();
			return frontEnds;
		}
		finally
		{
			lock_frontEnds.readLock().unlock();
		}
	}
	
}