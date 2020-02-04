package mcenderdragon.files.duplicates;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mcenderdragon.files.duplicates.HashedFileStorage.FileHash;

public class Main 
{
	public static ExecutorService fileIO;
	public static ExecutorService hashing;
	
	public static boolean running = true;
	
	static
	{
		int n = Math.max(1, Runtime.getRuntime().availableProcessors() -1);
		hashing = Executors.newFixedThreadPool(n);
		fileIO = Executors.newFixedThreadPool(n);
	}
	
	public static class TimedSaving implements Runnable
	{
		public final Serializable toSave;
		public final File file;
		
		public TimedSaving(Serializable toSave, File file) 
		{
			super();
			this.toSave = toSave;
			this.file = file;
		}

		@Override
		public void run() 
		{
			while(running)
			{
				try 
				{
					Thread.sleep(1000 * 60);
				}
				catch (InterruptedException e) 
				{
					e.printStackTrace();
				}

				try 
				{
					ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
					out.writeObject(toSave);
					out.close();
				} 
				catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			try 
			{
				ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
				out.writeObject(toSave);
				out.close();
			} 
			catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public static void load(File hashes, String parentFolder, Predicate<File> isValid)
	{
		HashedFileStorage storage;
		if(hashes.exists())
		{
			try
			{
				ObjectInputStream in = new ObjectInputStream(new FileInputStream(hashes));
				storage = (HashedFileStorage) in.readObject();
				in.close();
			}
			catch (Exception e) 
			{
				e.printStackTrace();
				return;
			}
		}
		else
		{
			storage = new HashedFileStorage();
		}
		
		Thread timer = new Thread(new TimedSaving(storage, hashes), "Map Saver");
		timer.start(); //saving every minute
		storage.addListener(h -> onDuplicate(storage, h));
		
		FolderWalker walker = new FolderWalker(parentFolder, storage, isValid);
		for(int i=0;i<10;i++)
		{
			walker.work(() -> {
				try 
				{
					ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(hashes));
					out.writeObject(storage);
					out.close();
				} 
				catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		
			try 
			{
				Thread.sleep(5000);
			} catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
		
		
		Main.hashing.shutdown();
		Main.fileIO.shutdown();
		
		running = false;
		try 
		{
			timer.join();
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static void load(String parentFolder)
	{
		File parent = new File(parentFolder).getAbsoluteFile();
		parent.mkdirs();
		File dataDir = new File(parent, ".duplicate_info");
		dataDir.mkdir();
		
		File hashes = new File(dataDir, "hashes2file.map");
		File blacklist = new File(dataDir, "blacklist.regex");
		if(!blacklist.exists())
		{
			try {
				blacklist.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try 
		{
			final Predicate<File>[] blacklistPred = Files.lines(blacklist.toPath())
				.map(Pattern::compile)
				.map(p -> (Predicate<File>)(File f) -> p.matcher(f.toString()).matches()).toArray(Predicate[]::new);
			
			Predicate<File> isValid = new Predicate<File>()
			{
				@Override
				public boolean test(File t) 
				{
					if(t.getName().equals(".duplicate_info"))
					{
						return false;
					}
					for(Predicate<File> b : blacklistPred)
					{
						if(b.test(t))
							return false;
					}
					return true;
				}
			};
			
			load(hashes, parent.toString(), isValid);
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
	}
	
	public static void onDuplicate(HashedFileStorage storage, FileHash hash)
	{
		System.out.println(Arrays.toString(storage.getFiles(hash).toArray()));
	}
	
	
	public static void main(String[] args) throws NoSuchAlgorithmException 
	{
		String path = ".";
		if(args.length>=1)
		{
			path = args[0];
		}
		System.out.println("Path to scan is set to '"+path+"'");
		load(path);
	}
}
