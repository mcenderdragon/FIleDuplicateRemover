package mcenderdragon.files.duplicates;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import mcenderdragon.files.duplicates.HashedFileStorage.FileHash;

public class FolderWalker 
{
	public static final String INFO = ".folder_info";
	
	private final Predicate<File> isValid;
	private final File root;
	private List<FolderToCheck> work;
	private HashedFileStorage storage;
	
	public FolderWalker(String parentFolder, HashedFileStorage storage, Predicate<File> isValid) 
	{
		this.isValid = isValid;
		this.root = new File(parentFolder);
		if(!root.exists())
		{
			throw new IllegalArgumentException("File " + parentFolder + " doesn't exist!");
		}
		work = new ArrayList<FolderToCheck>();
		this.storage = storage;
		
		enqueeWork(root);
	}
	
	public boolean shouldCheck(File f)
	{
		if(f.isFile() && f.getName().equals(INFO))
		{
			return false;
		}
		return isValid.test(f);
	}
	
	public CompletableFuture<FileHash> enqueeWork(File f)
	{
		if(shouldCheck(f))
		{
			if(f.isFile())
				return doFile(f);
			else if(f.isDirectory())
				doDirectory(f);
		}
		else
		{
			System.out.println("Ignoring " + f);
		}
		return null;
		
	}
	
	private CompletableFuture<FileHash> doFile(File f)
	{
		return storage.getHash(f);
	}
	
	private void doDirectory(File f)
	{
		synchronized (work) 
		{
			int s = work.size();
			FolderToCheck ftc = new FolderToCheck(f);
			if(!work.add(ftc))
			{
				System.err.println("Failed to add " + f);
				work.add(ftc);
			}
			Collections.sort(work);
			System.out.println("Folders Todo " + s +" -> " + work.size());
		}
	}
	
	public void work(Runnable callBack)
	{
		FolderToCheck ftc = null;
		int checked = 0;
		while(true)
		{
			if(ftc!=null)
			{
				synchronized (ftc) 
				{
					try {
						ftc.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				checked = 0;
			}
			
			FolderToCheck w;
			synchronized (work) 
			{
				if(work.isEmpty())
				{
					Main.hashing.execute(callBack);
					return;
				}
					
				int s = work.size();
				w = work.remove(0);
				System.out.println("Reduced work size from " + s + " to " + work.size() + Main.hashing + " " + Main.fileIO);
			}
			System.out.println("Searching " + w.folder);
			checked++;
			
			ArrayList<CompletableFuture<?>> list = new ArrayList<CompletableFuture<?>>();
			if(w.folder.listFiles()!=null)
			{
				List<File> dir = new ArrayList<File>();
				for(File f : w .folder.listFiles())
				{
					if(f.isDirectory())
						dir.add(f);
					else
					{
						CompletableFuture<FileHash> task = enqueeWork(f);
						if(task!=null)
							list.add(task);
					}
				}
				for(File f : dir)
				{
					CompletableFuture<FileHash> task = enqueeWork(f);
					if(task!=null)
						list.add(task);
				}
			}
			list.trimToSize();
			if(list.isEmpty())
			{
				w.done();
			}
			else
			{
				CompletableFuture.runAsync(() -> 
				{
					try
					{
						for(CompletableFuture<?> cf : list)
						{
							cf.join();
						}
						w.done();
					}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				}, Main.hashing);
			}
			if(checked > 1000)
			{
				ftc = w;
			}
		}
	}
	
	private static class FolderToCheck implements Comparable<FolderToCheck>
	{
		private final File folder;
		private final long modifedTime;
		
		public FolderToCheck(File folder) 
		{
			if(folder==null)
				System.err.println("Folder is null");
			Objects.requireNonNull(folder);
			this.folder = folder;			
			this.modifedTime = getLastSearch(folder);
		}
		
		public void done() 
		{
			File info = new File(folder, INFO);
			
			try 
			{
				info.createNewFile();
			} 
			catch (FileNotFoundException e)   {
				System.out.println(this + " " + e);
			} catch (IOException e) {
				System.out.println(this + " " + e);
			}
			
			System.out.println("Finished " + folder);
			synchronized (this)
			{
				this.notifyAll();
			}
		}
		
		@Override
		public String toString() 
		{
			return "" + folder;
		}

		private static long getLastSearch(File folder)
		{
			File info = new File(folder, INFO);
			if(info.exists())
			{
				return info.lastModified();
			}
			else
			{
				return -1;
			}
		}
		
		@Override
		public int compareTo(FolderToCheck o) 
		{
			return (int) (modifedTime - o.modifedTime);
		}
	}
	 
}
