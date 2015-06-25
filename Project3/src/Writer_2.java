import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.simple.JSONObject;

public class Writer_2 implements Runnable
{

	private long   start;
	private long   end;
	private String hash_tag;
	
	public Writer_2(String tag)
	{
		start = System.nanoTime();  
		hash_tag = tag;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		
		try {
			Socket socket = new Socket("localhost", 6051);
			BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

			String requestheaders = "GET /tweets?q=" + hash_tag + " HTTP/1.1\n";
			
			output.write(requestheaders);
			output.write("\n");
			output.flush();
			
			input.readLine();
			System.out.println(input.readLine());
			end = System.nanoTime();
			double seconds = (double)(end - start) / 1000000000.0;
			System.out.println(seconds);
			
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}