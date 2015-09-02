package com.gh.ghdownload.core;


import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.gh.ghdownload.DownloadConfig;
import com.gh.ghdownload.core.ConnectThread.ConnectListener;
import com.gh.ghdownload.core.DownloadThread.DownloadListener;
import com.gh.ghdownload.entity.DownloadEntry;
import com.gh.ghdownload.entity.DownloadEntry.DownloadStatus;
import com.gh.ghdownload.utils.FileUtils;
import com.gh.ghdownload.utils.Trace;

/**
 * 
 * @author shuwoom
 * @email 294299195@qq.com
 * @date 2015-9-2
 * @update 2015-9-2
 * @des A download task which contains an download entry.
 */
@SuppressLint("UseSparseArrays")
public class DownloadTask implements ConnectListener, DownloadListener{

	private final DownloadEntry entry;
	private volatile boolean isPaused = false;
	private volatile boolean isCancelled = false;
	private final Handler mHandler;
	private DownloadThread[] downloadThreads;
	private ConnectThread connectThread;
	private final ExecutorService mExecutor;
	private long lastStamp = 0;
	private DownloadStatus[] downloadStatus;
	
	private Long[] lastModifiedTime;//用于记录下线程的最后活动时间，监测线程是否alive
	private long subThreadRefreshInterval = 10000;


	public DownloadTask(DownloadEntry entry, Handler handler, ExecutorService executor) {
		this.entry = entry;
		mHandler = handler;
		mExecutor = executor;
	}

	public void start() {
		
		if(entry.totalLength > 0){
			//本地数据库有历史记录
			Trace.d("DownloadTask===>start()#####no need to request content length!");
			startDownload();
		}else{
			//第一次下载
			entry.status = DownloadStatus.connecting;
			Trace.d("DownloadTask===>start()#####first start download" + "*****" + entry.toString());
			notifyUpdate(entry, DownloadService.NOTIFY_CONNECTING);
			connectThread = new ConnectThread(entry.url, this);
			mExecutor.execute(connectThread);
		}
	}
	
	
	
	private void notifyUpdate(DownloadEntry entry, int what) {
		Message msg = mHandler.obtainMessage();
		msg.what = what;
		msg.obj = entry;
		mHandler.sendMessage(msg);
		
		try {
           Thread.sleep(10);
        } catch (InterruptedException e) {
           e.printStackTrace();
        }
		
	}

	public void pause(){
		Trace.d("DownloadTask==>pause()");
		isPaused = true;
		if(connectThread != null && connectThread.isRunning()){
			connectThread.cancel();
		}
		
		if(downloadThreads != null && downloadThreads.length > 0){
			for (DownloadThread thread : downloadThreads) {
				if(thread != null && thread.isRunning()){
					if(entry.isSupportRange){
						thread.pause();
					}else{
						thread.cancel();
					}
				}
			}
		}
	}
	
	public void cancel(){
		Log.v("gh_download", "DownloadTask==>cancel!!!!!");
		isCancelled = true;
		if(connectThread != null && connectThread.isRunning()){
			connectThread.cancel();
		}
		
		if(downloadThreads != null && downloadThreads.length > 0){
			for (DownloadThread thread : downloadThreads) {
				if(thread != null && thread.isRunning()){
					thread.cancel();
				}
			}
		}
	}

	private void startMultiThreadDownload(){
		subThreadRefreshInterval  = DownloadConfig.getSubThreadRefrashInterval(entry.totalLength);
		entry.status = DownloadStatus.downloading;
		notifyUpdate(entry, DownloadService.NOTIFY_DOWNLOADING);
		
		int startPos = 0;
		int endPos = 0;
		int block = entry.totalLength / DownloadConfig.getInstance().getMax_download_threads();
		
		if(entry.ranges == null){
			entry.ranges = new HashMap<Integer, Integer>();
			for(int i = 0; i < DownloadConfig.getInstance().getMax_download_threads(); i++){
				entry.ranges.put(i, 0);
			}
		}
		
		downloadThreads = new DownloadThread[DownloadConfig.getInstance().getMax_download_threads()];
		downloadStatus = new DownloadStatus[DownloadConfig.getInstance().getMax_download_threads()];
		lastModifiedTime = new Long[DownloadConfig.getInstance().getMax_download_threads()];
		for(int i = 0; i < DownloadConfig.getInstance().getMax_download_threads(); i++){
			lastModifiedTime[i] = System.currentTimeMillis();
		}
		
		for(int i = 0; i < DownloadConfig.getInstance().getMax_download_threads(); i++){
			startPos = i * block + entry.ranges.get(i);
			if(i == DownloadConfig.getInstance().getMax_download_threads() - 1){
				endPos = entry.totalLength - 1;
			}else{
				endPos = (i + 1) * block - 1;
			}
			if(startPos < endPos){
				downloadThreads[i] = new DownloadThread(entry.url, i, startPos, endPos, this);
				downloadStatus[i] = DownloadStatus.downloading;
				mExecutor.execute(downloadThreads[i]);
			}else{
				downloadStatus[i] = DownloadStatus.done;
			}
		}
		
		//判断文件是否已经下载完成
		boolean flag = true;
		for(int i = 0; i < downloadStatus.length; i++){
			if(downloadStatus[i] != DownloadStatus.done)
				flag = false;
		}
		
		if(flag){
			entry.status = DownloadStatus.done;
			notifyUpdate(entry, DownloadService.NOTIFY_COMPLETED);
			Trace.d("DownloadTask==>" + "startMultiThreadDownload#####" +entry.name + " is already done");
		}
	}
	
	private void startSingleThreadDownload(){
		entry.status = DownloadStatus.downloading;
		Trace.d(entry.toString());
		notifyUpdate(entry, DownloadService.NOTIFY_DOWNLOADING);
		
		downloadThreads = new DownloadThread[1];
		downloadThreads[0] = new DownloadThread(entry.url, 0, 0, 0, this);
		downloadStatus = new DownloadStatus[1];
		mExecutor.execute(downloadThreads[0]);
	}
	
	private void startDownload() {
		//判断存储卡剩余控件是否充足
		if(FileUtils.getStorageAvailableSize() <= entry.totalLength){
			entry.status = DownloadStatus.pause;
			Trace.d("DownloadTask==>" + "onConnectSuccess##### not enough storage size!!!");
			notifyUpdate(entry, DownloadService.NOTIFY_NOT_ENOUGH_SIZE);
			return;
		}
		
		if(entry.isSupportRange){
			Trace.d("DownloadTask()==>" + "startDownload#####start multi thread download!!!!");
			startMultiThreadDownload();
		}else{
			Trace.d("DownloadTask()==>" + "startDownload#####start single thread download!!!!");
			startSingleThreadDownload();
		}
		
	}
	
	@Override
	public void onConnectSuccess(boolean isSupportRange, int totalLength) {
		entry.isSupportRange = isSupportRange;
		entry.totalLength = totalLength;
		Trace.d("DownloadTask==>" + "onConnectSuccess#####" + entry.toString());
		startDownload();
	}


	@Override
	public void onConnectFaile(String message) {
		if (isPaused || isCancelled) {
            entry.status = isPaused ? DownloadEntry.DownloadStatus.pause : DownloadEntry.DownloadStatus.cancel;
            notifyUpdate(entry, DownloadService.NOTIFY_PAUSED_OR_CANCELLED);
            Trace.d("DownloadTask==>" + "onConnectFaile#####" + "isPaused or isCancelled is true*****" + entry.toString());
        } else {
            entry.status = DownloadEntry.DownloadStatus.error;
            notifyUpdate(entry, DownloadService.NOTIFY_ERROR);
            Trace.d("DownloadTask==>" + "onConnectFaile#####" + "error*****" + entry.toString());
        }
	}

	@Override
	public synchronized void onProgressChanged(int index, int progress) {
		
		if(entry.isSupportRange){
			int range = entry.ranges.get(index) + progress;
			entry.ranges.put(index, range);
			if(DownloadConfig.getInstance().getMax_download_threads() > 1){
				checkAndRefreshSubThread(index);
			}
		}
		entry.currentLength += progress;
		
		long stamp = System.currentTimeMillis();
		if(stamp - lastStamp  > 1000){
			lastStamp = stamp;
			int percent = (int)(entry.currentLength * 100l / entry.totalLength);
			entry.percent = percent;
			Log.v("gh_download", "index==>" + index + " " + entry.toString());
			notifyUpdate(entry, DownloadService.NOTIFY_UPDATING);
		}
		
		
	}

	//检查线程是否alive，如果线程在30内没有更新时间，认为线程dead，重启线程
	private void checkAndRefreshSubThread(int index) {
		long stamp = System.currentTimeMillis();
		int startPos = 0;
		int endPos = 0;
		int block = entry.totalLength / DownloadConfig.getInstance().getMax_download_threads();
		
		for (int i = 0; i < downloadThreads.length; i++) {
			if(stamp - lastModifiedTime[i] > 2 * subThreadRefreshInterval){
				//重启线程i
				startPos = i * block + entry.ranges.get(i);
				if(i == DownloadConfig.getInstance().getMax_download_threads() - 1){
					endPos = entry.totalLength - 1;
				}else{
					endPos = (i + 1) * block - 1;
				}
				if(startPos < endPos){
					Trace.d("DownloadTask==>onProgressChanged()###" + " restart sub-thread " + i);
					downloadThreads[i].pause();
					downloadThreads[i] = null;
					downloadThreads[i] = new DownloadThread(entry.url, i, startPos, endPos, this);
					downloadStatus[i] = DownloadStatus.downloading;
					mExecutor.execute(downloadThreads[i]);
				}else{
					downloadStatus[i] = DownloadStatus.done;
				}
				lastModifiedTime[i] = System.currentTimeMillis();
			}
		}
		
		//刷新线程index时间
		if(stamp - lastModifiedTime[index] > subThreadRefreshInterval){
			lastModifiedTime[index] = stamp;
			Trace.d("DownloadTask==>checkAndRefreshSubThread()##### index: " 
			+ index + " refresh sub-thread time***" + stamp);
		}
	}

	@Override
	public synchronized void onDownloadCompleted(int index) {
		Trace.d("DownloadTask==>onDownloadCompleted():index==>" + index);
		downloadStatus[index] = DownloadStatus.done;
		
		for(int i = 0; i < downloadStatus.length; i++){
			if(downloadStatus[i] != DownloadStatus.done){
				return;
			}
		}
		
		if(entry.totalLength > 0 && entry.currentLength != entry.totalLength){
			//下载出现异常，文件不完整,要清除，重新下载
			entry.status = DownloadStatus.error;
			entry.reset();
			notifyUpdate(entry, DownloadService.NOTIFY_ERROR);
			Trace.d("DownloadTask==>onDownloadCompleted()#####file is error, reset it!!!!!");
		}else{
			//文件下载完成，没有异常
			entry.status = DownloadStatus.done;
			entry.percent = 100;
            notifyUpdate(entry, DownloadService.NOTIFY_COMPLETED);
            Trace.d("DownloadTask==>onDownloadCompleted()#####file is ok!!!!!");
		}
	}

	@Override
	public synchronized void onDownloadError(int index, String message) {
		Trace.e("onDownloadError:" + message);
		downloadStatus[index] = DownloadStatus.error;
		for(int i = 0; i < downloadStatus.length; i++){
			if(downloadStatus[i] != DownloadStatus.done 
					&& downloadStatus[i] != DownloadStatus.error){
				downloadThreads[i].cancelByError();
				return;
			}
		}
		
		entry.status = DownloadStatus.error;
    	notifyUpdate(entry, DownloadService.NOTIFY_ERROR);
	}

	@Override
	public synchronized void onDownloadPaused(int index) {
		downloadStatus[index] = DownloadStatus.pause;
		for(int i = 0; i < downloadStatus.length; i++){
			if(downloadStatus[i] != DownloadStatus.done 
					&& downloadStatus[i] != DownloadStatus.pause){
				return;
			}
		}
		entry.status = DownloadStatus.pause;
		Trace.d(entry.toString());
		notifyUpdate(entry, DownloadService.NOTIFY_PAUSED_OR_CANCELLED);
	}

	@Override
	public synchronized void onDownloadCanceled(int index) {
		downloadStatus[index] = DownloadStatus.cancel;
		for(int i = 0; i < downloadStatus.length; i++){
			if(downloadStatus[i] != DownloadStatus.done 
					&& downloadStatus[i] != DownloadStatus.cancel){
				return;
			}
		}
		
		entry.status = DownloadStatus.cancel;
		Trace.d(entry.toString());
		entry.reset();
		notifyUpdate(entry, DownloadService.NOTIFY_PAUSED_OR_CANCELLED);
	}

}
