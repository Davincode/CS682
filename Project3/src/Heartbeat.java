import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class Heartbeat{
	
	Timer timer;
	Discover service;
	
	public Heartbeat(Discover server)
	{
		service = server;
		timer = new Timer();
		ArrayList<String> nodes = service.getLiveNodes();
		timer.schedule(new Task(nodes), 0, 1000);
	}
	
	public void reset()
	{
		timer = new Timer();
		ArrayList<String> nodes = service.getLiveNodes();
		timer.schedule(new Task(nodes), 0, 1000);
	}
	
	class Task extends TimerTask
	{	
		ArrayList<String> nodes;
		
		public Task(ArrayList<String> ns)
		{
			nodes = ns;
		}
		
		public void run() {
			String ip_address = new String();
			try
			{
				for (int i = 0; i < nodes.size(); i++)
				{
					ip_address = nodes.get(i);
					Socket socket = new Socket(ip_address, 6050);
					BufferedReader input =
				            new BufferedReader(new InputStreamReader(socket.getInputStream()));
					BufferedWriter output = 
						new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
					
					String requestheaders = "GET /heartbeat HTTP/1.1\n";
					
					output.write(requestheaders);
					output.write("\n");
					output.flush();
					
				}
			
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
				timer.cancel();
				service.unregister(ip_address);
			}
		}
	}
}
