import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class FrontEndService implements Runnable {

	private Socket socket;
	private FrontEnd service;
	private JSONParser parser = new JSONParser();
	
	public FrontEndService(Socket s, FrontEnd serve)
	{
		socket = s;
		service = serve;
	}
	
	public void run(){
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
					&& request.getUripath().equals("/tweets")
					&& request.getHttpversion().equals("HTTP/1.1"))
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

				String fromDS = new String();
				while (true)
				{
					String init = service.getPrimary();
					fromDS = service.add(object);
					if (fromDS != null || fromDS.length() != 0) 
					{
						break;
					}
					else
					{
						while (true)
						{
							String current = service.getPrimary();
							if (!init.equals(current))
							{
								service.setPrimary(current);
								break;
							}
						}
					}
				}
				
				if (fromDS.equals("no hashtag"))
				{
					responsebody = "<html><body>400 Bad Request</body></html>";
					responseheaders = "HTTP/1.1 400 Bad Request\r\n\r\n";
				}
				else
				{
					responsebody = fromDS;
					responseheaders = "HTTP/1.1 201 Created for valid request\r\n\r\n";
				}
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.POST
					&& request.getUripath().startsWith("/primary")
					&& request.getHttpversion().equals("HTTP/1.1"))
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
				
				service.setPrimary((String)object.get("primary"));
				responsebody = "201 Created for valid request";
				responseheaders = "HTTP/1.1 201 Created for valid request\n";
			}
			else if (request.getMethod() == HTTPConstants.HTTPMethod.GET
					&& request.getUripath().startsWith("/tweets?")
					&& request.getHttpversion().equals("HTTP/1.1"))
			{
				if (request.getParameters().containsKey("q"))
				{
					String q = request.getParameters().get("q");
					
					JSONObject object = null;
					while (true)
					{
						String init = service.getPrimary();
						object = service.query(q);
						if (object.size() != 0) 
						{
							break;
						}
						else
						{
							while (true)
							{
								String current = service.getPrimary();
								if (!init.equals(current))
								{
									service.setPrimary(current);
									break;
								}
							}
						}
					}
					
					responsebody = "<html><body>" + object.toString() + "</body></html>";
					responseheaders = "HTTP/1.1 200 OK\r\n\r\n";
				}
				else
				{
					responsebody = "<html><body>400 Bad Request</body></html>";
					responseheaders = "HTTP/1.1 400 Bad Request\r\n\r\n";
				}
			}
			else
			{
				responsebody = "<html><body>404 Not Found</body></html>";
				responseheaders = "HTTP/1.1 404 Not Found\r\n\r\n";
			}
			System.out.println(responseheaders);
			System.out.println(responsebody);
			output.write(responseheaders);
			output.write(responsebody);
			output.write("\n");
			output.flush();
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
