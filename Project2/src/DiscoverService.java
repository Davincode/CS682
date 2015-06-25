import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

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
			
			String responseheaders = new String();
			String responsebody = new String();
			
			if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/register")
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
				
				int id = discover.register((String)jsonObject.get("ip_address"));
				
				JSONObject result = new JSONObject();
				result.put("primary", discover.getPrimary());
				result.put("id", Integer.toString(id));
				responsebody = result.toString();
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/registerFrontEnd")
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
				
				discover.addFrontEnd((String)jsonObject.get("ip_address"));
				
				responsebody = "201 Created for valid request";
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/livenodes?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				JSONObject jsonObject = null;
				try {
					jsonObject = (JSONObject) parser.parse(input.readLine());
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
				JSONArray nodes = discover.getLiveNodes((String)jsonObject.get("ip_address"));
				
				responsebody = nodes.toString();
				responseheaders = "HTTP/1.1 200 OK\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().startsWith("/primary")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				JSONObject object = new JSONObject();
				String jsonString = input.readLine();
				
				try {
					object = (JSONObject) parser.parse(jsonString);
				} catch (ParseException pe) {
			      System.out.println("Error: could not parse JSON response:");
			      System.out.println(request);
			      System.exit(1);
				}
				discover.unregister(discover.getPrimary());
				
				String primary = (String) object.get("primary");
				discover.setPrimary(primary);
				
				JSONArray livenodes = (JSONArray) object.get("servers");
				discover.setLiveNodes(livenodes);
				
				discover.informFrontEnd();
				
				responsebody = "201 Created for valid request";
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/primary?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				JSONObject object = new JSONObject();
				object.put("primary", discover.getPrimary());
				responsebody = object.toString();
				responseheaders = "HTTP/1.1 200 OK\n";
			}
			else
			{
				responsebody = "<html><body>400 Bad Request</body></html>";
				responseheaders = "HTTP/1.1 400 Bad Request\n";
			}
			
			System.out.println(responseheaders);
			System.out.println(responsebody);
			output.write(responseheaders);
			output.write(responsebody);
			output.write("\n");
			output.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}
