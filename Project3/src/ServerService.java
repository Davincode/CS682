import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class ServerService implements Runnable {
	
	private Socket socket;
	private Server service;
	private int    counter;
	
	public ServerService(Socket s, Server serve)
	{
		socket = s;
		service = serve;
		counter = 0;
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
			counter += 1;
			
			String responseheaders = new String();
			String responsebody = new String();
			
			// for starter
			if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().equals("/replicateAll")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				responsebody = service.replicateTo().toString();
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
				
				System.out.println(header);
				System.out.println(responsebody);
			}
			
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/register")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				String value = input.readLine();
				JSONArray array = service.register(value);
				responsebody = array.toString();
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
				
				System.out.println(header);
				System.out.println(value);
				System.out.println(responsebody);
			}
			
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/unregister")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				String value = input.readLine();
				counter = 0;
				service.unregister(value);
				responsebody = "201 Created for valid request";
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
				
				System.out.println(header);
				System.out.println(value);
				System.out.println(responsebody);
			}
			
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().equals("/counter?")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				responsebody = Integer.toString(counter);
				responseheaders = "HTTP/1.1 200 OK\n";
				
				System.out.println(header);
				System.out.println(responsebody);
			}

			// for basic functionality
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/tweets")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				String value = input.readLine();
				service.write(value);
				
				JSONObject object = service.makeTimestamp();
				
				responsebody = object.toString();
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
				
				System.out.println(header);
				System.out.println(value);
				System.out.println(responsebody);
				output.write(responseheaders);
				output.write(responsebody);
				output.write("\n");
				output.flush();
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				// broadcast the new value
				service.broadcastWrite(value);
				return;
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().equals("/broadcastTweets")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				String value = input.readLine();
				System.out.println(header);
				System.out.println(value);
				service.writeFromBroadcast(value);
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/tweets?")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				String q = request.getParameters().get("q");
				
				String value = input.readLine();
				
				int counter = 0;
				while(true)
				{
					JSONObject object = service.read(q, value);
					counter += 1;
					
					if (((String)object.get("isUpdate")).equals("Y"))
					{
						responsebody = object.toString();
						responseheaders = "HTTP/1.1 200 OK\n";
						break;
					}
					else
					{
						responsebody = object.toString();
					}
					
					if(counter > 10) break;
				}
				
				System.out.println(header);
				System.out.println(value);
				System.out.println(responsebody);
			}
			
			// for detection and fault tolerance
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/heartbeat")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				responsebody = "200 OK";
				responseheaders = "HTTP/1.1 200 OK\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/replicateSome")
					&& request.getHttpversion().equals("HTTP/1.1") )
			{
				String value = input.readLine();
				JSONArray array = service.replicate(value);
				responsebody = array.toString();
				responseheaders = "HTTP/1.1 200 OK\n";
				
				System.out.println(header);
				System.out.println(value);
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
