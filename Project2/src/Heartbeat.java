import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;


public class Heartbeat{
	
	Timer timer;
	Server service;
	
	public Heartbeat(Server server)
	{
		service = server;
		timer = new Timer();
		timer.schedule(new Task(), 0, 1000);
	}
	
	public void reset()
	{
		timer = new Timer();
		timer.schedule(new Task(), 0, 1000);
	}
	
	class Task extends TimerTask
	{
		public void run() {
			if (service.getPrimary() == null || service.getPrimary().equals(service.getIp_address())) return;
			try
			{
				Socket socket = new Socket(service.getPrimary(), 6050);
				BufferedReader input =
			            new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = 
					new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				String requestheaders = "GET /heartbeat HTTP/1.1\n";
				
				output.write(requestheaders);
				output.write("\n");
				output.flush();
				
				System.out.println(input.readLine());
				System.out.println(input.readLine());
			
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
				timer.cancel();
				service.election();
			}
		}
	}
}
