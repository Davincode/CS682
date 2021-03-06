import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class FrontEndProcessor{

	private static int    portNumber;
	private static int    poolSize = 10;
	
	public static void main(String[] args)
	{
		if (args.length != 1) return;
		
		portNumber = Integer.parseInt(args[0]);
		ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		FrontEnd service = new FrontEnd();
		
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(portNumber);
		} catch (IOException e) {
			e.printStackTrace();
		}
	    
	    while (true)
	    {
	    	Socket socket = null;
			try {
				socket = serverSocket.accept();
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	pool.execute(new FrontEndService(socket, service));
	    }
	}
}