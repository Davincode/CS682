import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import org.json.simple.JSONObject;


public class ServerService implements Runnable {
	
	private Socket socket;
	private Server service;
	
	public ServerService(Socket s, Server serve)
	{
		socket = s;
		service = serve;
	}

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
			
			// for starter
			if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().equals("/replicateAll")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				responsebody = service.replicateTo().toString();
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}

			// for basic functionality
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/tweets")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				String value = input.readLine();
				service.write(value);
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				// broadcast the new value
				if (service.getPrimary().equals(service.getIp_address()))
				{
					service.broadcastWrite(value);
				}
				
				responsebody = "<html><body>201 Created for valid request</body></html>";
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.DELETE
					&& request.getUripath().equals("/tweets")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				service.rollback(input.readLine());
				responsebody = "<html><body>201 Created for valid request</body></html>";
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/tweets?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				String q = request.getParameters().get("q");
				String v = request.getParameters().get("v");
				JSONObject object = service.read(q, v);
				
				if (v.equals((String)object.get("v")))
				{
					responsebody = "<html><body>304 Not Modified</body></html>";
					responseheaders = "HTTP/1.1 304 Not Modified\n";
				}
				else
				{
					responsebody = object.toString();
					responseheaders = "HTTP/1.1 200 OK\n";
				}
			}
			
			// for detection and election
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/heartbeat")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				responsebody = "<html><body>200 OK</body></html>";
				responseheaders = "HTTP/1.1 200 OK\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/alive?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				String ip_address = request.getParameters().get("my_ip_address");
				responsebody = "alive";
				responseheaders = "HTTP/1.1 200 OK\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().startsWith("/getLikeMe")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				service.getLikeMe(input.readLine());
				responsebody = "";
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
