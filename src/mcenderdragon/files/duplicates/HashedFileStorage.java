package mcenderdragon.files.duplicates;

import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class HashedFileStorage implements Externalizable
{
	private transient final Lock read, write;
	private Map<String, FileHash> file2Hash;
	private transient Map<FileHash, List<String>> hash2file;
	private transient List<Consumer<FileHash>> duplicatedHashConsumer;
	
	public HashedFileStorage()
	{
		ReadWriteLock rwlock = new ReentrantReadWriteLock();
		read = rwlock.readLock();
		write = rwlock.writeLock();
		
		file2Hash = new HashMap<String, HashedFileStorage.FileHash>();
		hash2file = new HashMap<HashedFileStorage.FileHash, List<String>>();
		
		duplicatedHashConsumer = new ArrayList<Consumer<FileHash>>(8);
	}
	
	@Override
	public String toString() 
	{
		return "HashedFileStorage " + file2Hash.size() + " " + hash2file.size();
	}
	
	private void putHash(File f, FileHash hash)
	{
		String path = f.getAbsoluteFile().toString();
		write.lock();
		file2Hash.put(path, hash);
		List<String> l = hash2file.computeIfAbsent(hash, h -> new ArrayList<>(2));
		l.add(path);
		int size = l.size();
		write.unlock();
		if(size>1)
			duplicatedHashConsumer.forEach(c -> c.accept(hash));
	}
	
	public CompletableFuture<FileHash> getHash(File f)
	{
		return CompletableFuture.supplyAsync(() -> 
		{
			read.lock();
			String path = f.getAbsoluteFile().toString();
			FileHash h = file2Hash.getOrDefault(path, null);
			read.unlock();
			if(h!=null)
			{
				long lastEdit = f.lastModified();
				if(lastEdit > h.hashDate)
				{
					h = compute(f).join();
					putHash(f, h);
				}
				return h;
			}
			else
			{
				h = compute(f).join();
				putHash(f, h);
				return h;
			}
		}, Main.hashing);
	}
	
	public static CompletableFuture<FileHash> compute(File f)
	{
		File path = f.getAbsoluteFile();
		return CompletableFuture.supplyAsync(() -> 
		{
			System.out.println("Computing Hash of " + f);
			try 
			{
				MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
				FileInputStream in = new FileInputStream(path);
				byte[] buffer = new byte[1024 * 1024]; //read 1Mb at a time
				while(in.available() > 0)
				{
					int read = in.read(buffer);
					sha256.update(buffer, 0, read);
				}
				in.close();
				FileHash hash = new FileHash(sha256.digest());
				return hash;
			}
			catch (FileNotFoundException e)  {
				e.printStackTrace();
			} catch (IOException e)  {
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			return null;
		}, Main.fileIO);
	}
	
	public static class FileHash implements Serializable
	{
		private static final long serialVersionUID = 2179001434498564107L;
		public final byte[] hash;
		public final long hashDate;
		private transient Integer hashCode;
		
		public FileHash(byte[] hash) 
		{
			super();
			this.hash = hash;
			hashDate = System.currentTimeMillis();
		}
		
		@Override
		public int hashCode() 
		{
			if(hashCode == null)
			{
				hashCode= 0;
				for(byte b : hash)
				{
					hashCode =  b + hashCode* 31;
				}
			}
			
			return hashCode;
		}
		
		@Override
		public boolean equals(Object obj) 
		{
			if(obj==null)
				return false;
			else if(obj ==this)
				return true;
			else if(obj instanceof FileHash)
			{
				byte[] o = ((FileHash) obj).hash;
				return Arrays.equals(o, hash);
			}
			else
				return false;
		}
		
		@Override
		public String toString() 
		{
			return new String(hash, StandardCharsets.UTF_8);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException 
	{
		write.lock();
		file2Hash = (Map<String, FileHash>) in.readObject();
		file2Hash.forEach((file,hash) -> hash2file.computeIfAbsent(hash, kk -> new ArrayList<>()).add(file));
		write.unlock();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException 
	{
		read.lock();
		out.writeObject(file2Hash);
		read.unlock();
	}

	public void addListener(Consumer<FileHash> listener) 
	{
		this.duplicatedHashConsumer.add(listener);
	}

	public List<String> getFiles(FileHash hash) 
	{
		read.lock();
		List<String> files = hash2file.getOrDefault(hash, Collections.emptyList());
		read.unlock();
		return files;
	}
}
