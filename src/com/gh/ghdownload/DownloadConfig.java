package com.gh.ghdownload;

import java.io.File;

import android.os.Environment;

public class DownloadConfig {
	private static DownloadConfig mInstance;
	
	private int max_download_tasks = 3;
    private int max_download_threads = 3;
    private File downloadDir = null;
    private int min_operate_interval = 1000 * 1;
    private boolean recoverDownloadWhenStart = false;
    public static String DOWNLOAD_PATH = Environment.getExternalStorageDirectory() + File.separator +
    		"gh-download" + File.separator;
    
	private DownloadConfig(){
        downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
	}
	
	public static long getSubThreadRefrashInterval(int fileSize){
		if(fileSize <= 1024 * 1024 * 20){
			//小于20M
			return 2 * 1000;
		}else if(fileSize > 1024 * 1024 * 20 && fileSize <= 1024 * 1024 * 100){
			//20M~100M
			return 10 * 1000;
		}else{
			//>100M
			return 20 * 1000;
		}
	}
	
	public static DownloadConfig getInstance(){
		if(mInstance == null){
			mInstance = new DownloadConfig();
		}
		return mInstance;
	}

	public int getMax_download_tasks() {
		return max_download_tasks;
	}

	public int getMax_download_threads() {
		return max_download_threads;
	}

	public File getDownloadDir() {
		return downloadDir;
	}

	public int getMin_operate_interval() {
		return min_operate_interval;
	}

	public boolean isRecoverDownloadWhenStart() {
		return recoverDownloadWhenStart;
	}
	
	


}
