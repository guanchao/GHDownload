package com.gh.ghdownload.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.Toast;

import com.gh.ghdownload.DownloadConfig;
import com.gh.ghdownload.db.DBController;
import com.gh.ghdownload.entity.DownloadEntry;
import com.gh.ghdownload.entity.DownloadEntry.DownloadStatus;
import com.gh.ghdownload.notify.DataChanger;
import com.gh.ghdownload.utils.Constants;
import com.gh.ghdownload.utils.Trace;

/**
 * 
 * @author shuwoom
 * @email 294299195@qq.com
 * @date 2015-9-2
 * @update 2015-9-2
 * @des Service to manager download tasks.
 */
public class DownloadService extends Service{
	public static final int NOTIFY_DOWNLOADING = 1;
    public static final int NOTIFY_UPDATING = 2;
    public static final int NOTIFY_PAUSED_OR_CANCELLED = 3;
    public static final int NOTIFY_COMPLETED = 4;
	public static final int NOTIFY_ERROR = 5;
	public static final int NOTIFY_CONNECTING = 6;
	public static final int NOTIFY_NOT_ENOUGH_SIZE = 7;
	
	private HashMap<String, DownloadTask> mDownloadingTasks;
	private LinkedBlockingQueue<DownloadEntry> mWaitingQueue;
	
	private ExecutorService mExecutor;
	
	private Handler mHandler = new Handler(){
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			DownloadEntry entry = (DownloadEntry) msg.obj;
			switch(msg.what){
				case NOTIFY_COMPLETED:
				case NOTIFY_PAUSED_OR_CANCELLED:
				case NOTIFY_ERROR:
					checkNext(entry);
				break;
				
				case NOTIFY_NOT_ENOUGH_SIZE:
					Toast.makeText(getApplicationContext(), "存储卡空间不足，请清理！", Toast.LENGTH_SHORT).show();
					checkNext(entry);
					break;
			}
			DataChanger.getInstance(getApplication()).updateStatus(entry);
		}

	};
	private DataChanger dataChanger;
	private DBController dbController;
	

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		mDownloadingTasks = new HashMap<String, DownloadTask>();
		mWaitingQueue = new LinkedBlockingQueue<DownloadEntry>();
		
		mExecutor = Executors.newCachedThreadPool();
		dataChanger = DataChanger.getInstance(getApplicationContext());
		dbController = DBController.getInstance(getApplicationContext());
		intializeDownload();
		 
	}
	
	//防止App进程被强杀时数据丢失
	private void intializeDownload() {
		ArrayList<DownloadEntry> mDownloadEtries = dbController.queryAll();
        if (mDownloadEtries != null) {
            for (DownloadEntry entry : mDownloadEtries) {
                if (entry.status == DownloadStatus.downloading || entry.status == DownloadStatus.waiting){
                    entry.status = DownloadStatus.pause;
                    if(DownloadConfig.getInstance().isRecoverDownloadWhenStart()){
                    	if(entry.isSupportRange){
                    		entry.status = DownloadStatus.pause;
                    	}else{
                    		entry.status = DownloadStatus.idle;
                    		entry.reset();
                    	}
                    	addDownload(entry);
                    }else{
                    	if(entry.isSupportRange){
                    		entry.status = DownloadStatus.pause;
                    	}else{
                    		entry.status = DownloadStatus.idle;
                    		entry.reset();
                    	}
                    	dbController.newOrUpdate(entry);
                    }
                }
                dataChanger.addToOperatedEntryMap(entry.url, entry);
            }
        }
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent != null){
			int action = intent.getIntExtra(Constants.KEY_DOWNLOAD_ACTION, -1);
			DownloadEntry entry = (DownloadEntry) intent.getSerializableExtra(Constants.KEY_DOWNLOAD_ENTRY);
			/*****防止App进程被强杀时数据丢失*****/
			if(entry != null && dataChanger.containsDownloadEntry(entry.url)){
				entry = dataChanger.queryDownloadEntryByUrl(entry.url);
			}
			
			switch(action){
				case Constants.KEY_DOWNLOAD_ACTION_ADD:
					addDownload(entry);
					break;
					
				case Constants.KEY_DOWNLOAD_ACTION_PAUSE:
					pauseDownload(entry);
					break;
					
				case Constants.KEY_DOWNLOAD_ACTION_RESUME:
					resumeDownload(entry);
					break;
					
				case Constants.KEY_DOWNLOAD_ACTION_CANCEL:
					cancelDownload(entry);
					break;
					
				case Constants.KEY_DOWNLOAD_ACTION_PAUSE_ALL:
					pauseAllDownload();
					break;
					
				case Constants.KEY_DOWNLOAD_ACTION_RECOVER_ALL:
					recoverAllDownload();
					break;
					
				default:
					break;
			
			}
		}
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	private void recoverAllDownload() {
		 ArrayList<DownloadEntry> mRecoverableEntries = DataChanger.getInstance(getApplication()).queryAllRecoverableEntries();
		if(mRecoverableEntries == null)	return;
		
		for (DownloadEntry entry : mRecoverableEntries) {
			addDownload(entry);
		}
		Trace.d("DownloadService==>recoverAllDownload" + "***Task Size:" + mDownloadingTasks.size() + "***Waiting Queue:" + mWaitingQueue.size());
	}

	private void pauseAllDownload() {
		while(mWaitingQueue.iterator().hasNext()){
			DownloadEntry entry = mWaitingQueue.poll();
			entry.status = DownloadStatus.pause;
			DataChanger.getInstance(getApplication()).updateStatus(entry);
		}
		
		for (Map.Entry<String, DownloadTask> entry : mDownloadingTasks.entrySet()) {
            entry.getValue().pause();
        }
		mDownloadingTasks.clear();
		Trace.d("DownloadService==>pauseAllDownload");
	}

	private void checkNext(DownloadEntry entry) {
		mDownloadingTasks.remove(entry.url);
		DownloadEntry newEntry = mWaitingQueue.poll();
		if(newEntry != null){
			startDownload(newEntry);
		}
	};

	private void cancelDownload(DownloadEntry entry) {
		DownloadTask task = mDownloadingTasks.remove(entry.url);
		if(task != null){
			task.cancel();
			Trace.d("DownloadService==>pauseDownload#####cancel downloading task" 
				+ "***Task Size:" + mDownloadingTasks.size() 
				+ "***Waiting Queue:" + mWaitingQueue.size());
		}else{
			mWaitingQueue.remove(entry);
			entry.status = DownloadStatus.cancel;
			DataChanger.getInstance(getApplication()).updateStatus(entry);
			Trace.d("DownloadService==>pauseDownload#####cancel waiting queue!" 
					+ "***Task Size:" + mDownloadingTasks.size() 
					+ "***Waiting Queue:" + mWaitingQueue.size());
		}
	}

	private void resumeDownload(DownloadEntry entry) {
		addDownload(entry);
		Trace.d("DownloadService==>resumeDownload" 
				+ "***Task Size:" + mDownloadingTasks.size() 
				+ "***Waiting Queue:" + mWaitingQueue.size());
	}

	private void pauseDownload(DownloadEntry entry) {
		DownloadTask task = mDownloadingTasks.remove(entry.url);
		if(task != null){
			Trace.d("DownloadService==>pauseDownload#####pause downloading task" 
					+ "***Task Size:" + mDownloadingTasks.size() 
					+ "***Waiting Queue:" + mWaitingQueue.size());
			task.pause();
		}else{
			mWaitingQueue.remove(entry);
			entry.status = DownloadStatus.pause;
			DataChanger.getInstance(getApplication()).updateStatus(entry);
			Trace.d("DownloadService==>pauseDownload#####pause waiting queue!" 
					+ "***Task Size:" + mDownloadingTasks.size() 
					+ "***Waiting Queue:" + mWaitingQueue.size());
		}
		
	}

	private void addDownload(DownloadEntry entry) {
		checkDownloadPath(entry);
		if(isDownloadEntryRepeted(entry)){
			return ;
		}
		if(mDownloadingTasks.size() >= DownloadConfig.getInstance().getMax_download_tasks()){
			mWaitingQueue.offer(entry);
			entry.status = DownloadStatus.waiting;
			DataChanger.getInstance(getApplication()).updateStatus(entry);
			Trace.d("DownloadService==>addDownload#####bigger than max_tasks"
					+ "***Task Size:" + mDownloadingTasks.size()
					+ "***Waiting Queue:" + mWaitingQueue.size());
		}else{
			Trace.d("DownloadService==>addDownload#####start tasks"
					+ "***Task Size:" + mDownloadingTasks.size() 
					+ "***Waiting Queue:" + mWaitingQueue.size());
			startDownload(entry);
		}
	}
	
	private void startDownload(DownloadEntry entry){
		DownloadTask task =new DownloadTask(entry, mHandler, mExecutor);
		mDownloadingTasks.put(entry.url, task);
		Trace.d("DownloadService==>startDownload"
				+ "***Task Size:" + mDownloadingTasks.size() 
				+ "***Waiting Queue:" + mWaitingQueue.size());
		task.start();
	}
	
	private void checkDownloadPath(DownloadEntry entry) {
		Trace.d("DownloadService==>checkDownloadPath()");
		File file = new File(DownloadConfig.DOWNLOAD_PATH + entry.url.substring(entry.url.lastIndexOf("/") + 1));
		if(file != null && !file.exists()){
			entry.reset();
			Trace.d("DownloadService==>checkDownloadPath()#####" + entry.name + "'s cache is not exist, restart download!");
		}
	}
	
	private boolean isDownloadEntryRepeted(DownloadEntry entry){
		if(mDownloadingTasks.get(entry.url) != null){
			Trace.d("DownlaodService==>isDownloadEntryRepeted()##### The downloadEntry is in downloading tasks!!");
			return true;
		}
		
		if(mWaitingQueue.contains(entry)){
			Trace.d("DownlaodService==>isDownloadEntryRepeted()##### The downloadEntry is in waiting queue!!");
			return true;
		}
		return false;
	}

}
