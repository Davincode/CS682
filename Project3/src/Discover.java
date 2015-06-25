import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class Discover {

	private int increment = 0;
	private ArrayList<String> nodes = new ArrayList<String>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private Heartbeat heartbeat;

	public void addHeartbeat(Heartbeat heart)
	{
		heartbeat = heart;
	}
	
	public void resetHeartbeat()
	{
		heartbeat.reset();
	}
	
	public Discover()
	{
		
	}
	
	public String balancer()
	{
		String result = new String();
		ArrayList<String> nodes = getLiveNodes();
		if(nodes.size() != 0)
		{
			result = nodes.get(increment % nodes.size());
			increment += 1;
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject register(String ip_address)
	{
		JSONObject result = new JSONObject();
		ArrayList<String> nodes = getLiveNodes();
		JSONArray array = new JSONArray();
		for (int i = 0; i < nodes.size(); i++)
		{
			array.add(nodes.get(i));
		}
		addNode(ip_address);
		result.put("servers", array);
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public void unregister(String ip_address)
	{
		try {
			lock.writeLock().lock();
			
			if(nodes.contains(ip_address))
			{
				nodes.remove(ip_address);
			}
		}
		finally
		{
			lock.writeLock().unlock();
		}
		
		for(int i = 0; i < nodes.size(); i++)
		{
			Socket socket;
			try {
				socket = new Socket(nodes.get(i), 6050);
				
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "POST /unregister HTTP/1.1\n";
				
				JSONObject object = new JSONObject();
				object.put("ip_address", ip_address);
				
				output.write(requestheaders);
				output.write(object.toString());
				output.write("\n");
				output.flush();
				
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		heartbeat.reset();
	}
	
	public void addNode(String ip_address)
	{
		try {
			lock.writeLock().lock();
			
			nodes.add(ip_address);
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}
	
	public ArrayList<String> getLiveNodes()
	{
		try {
			lock.readLock().lock();
			
			return nodes;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}
	
}