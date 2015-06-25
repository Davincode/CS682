import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class Cache {
	
	private HashMap<String, ArrayList<String>> cache = new HashMap<String, ArrayList<String>>();
	private HashMap<String, Integer>  tag_version = new HashMap<String, Integer>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	public int getVersion(String tag)
	{
		int version;
		try {
			lock.readLock().lock();
			if (tag_version.containsKey(tag))
			{
				version = tag_version.get(tag);
			}
			else
			{
				version = 0;
			}
		}
		finally
		{
			lock.readLock().unlock();
		}
		return version;
	}
	
	public void addCache(String tag, int version, ArrayList<String> temp)
	{
		try {
			lock.writeLock().lock();
			cache.put(tag, temp);
			tag_version.put(tag, version);
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

}
