import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.Provider.Service;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class DiscoverService implements Runnable {
	
	private Socket socket;
	private Discover discover;
	
	private JSONParser parser = new JSONParser();
	
	public DiscoverService(Socket s, Discover dis)
	{
		socket = s;
		discover = dis;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		try {
			BufferedReader input =
		            new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

			String header = input.readLine();
			HTTPRequestLine request = HTTPRequestLineParser.parse(header);
			System.out.println(header);
			
			String responseheaders = new String();
			String responsebody = new String();
			
			if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/registerBackEnd")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				JSONObject jsonObject = null;
				try {
					jsonObject = (JSONObject) parser.parse(input.readLine());
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
				
				String ip_address = (String)jsonObject.get("ip_address");
				JSONObject result = discover.register(ip_address);
				
				responsebody = result.toString();
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
				
				System.out.println(responsebody);
				output.write(responseheaders);
				output.write(responsebody);
				output.write("\n");
				output.flush();
				
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				discover.resetHeartbeat();
				return;
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/backend?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				JSONObject object = new JSONObject();
				object.put("ip_address", discover.balancer());
				
				responsebody = object.toString();
				responseheaders = "HTTP/1.1 200 OK\n";
				
				System.out.println(responsebody);
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/livenodes?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				ArrayList<String> nodes = discover.getLiveNodes();
				JSONArray result = new JSONArray();
				for(int i = 0; i < nodes.size(); i++)
				{
					result.add(nodes.get(i));
				}
				
				responsebody = result.toString();
				responseheaders = "HTTP/1.1 200 OK\n";
				
				System.out.println(responsebody);
			}
			else
			{
				responsebody = "400 Bad Request";
				responseheaders = "HTTP/1.1 400 Bad Request\n";
			}
			
			output.write(responseheaders);
			output.write(responsebody);
			output.write("\n");
			output.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}
