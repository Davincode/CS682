import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Test_2 {
	
	private static int poolSize;
	private static String tag;
	
	public static void main(String[] args)
	{
		if (args.length != 2) return;
		poolSize = Integer.parseInt(args[0]);
		tag = args[1];
		ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		
		for(int i = 0; i < poolSize; i++)
		{
			pool.execute(new Writer_2(tag));
		}
		pool.shutdown();
	}
}
