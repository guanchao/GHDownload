package com.gh.ghdownload.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import android.util.Log;

import com.gh.ghdownload.DownloadConfig;
import com.gh.ghdownload.entities.DownloadEntry;
import com.gh.ghdownload.entities.DownloadEntry.DownloadStatus;
import com.gh.ghdownload.utilities.Constants;
import com.gh.ghdownload.utilities.Trace;

public class DownloadThread implements Runnable{
	private String url;
	private int index;
	private int startPos;
	private int endPos;
	private String path;
	
	private DownloadListener listener;
	private volatile boolean isPaused ;
	private volatile boolean isCanceled;
	private volatile boolean isError;
	private boolean isSingleDownload;
	
	private DownloadStatus status;
	
	public DownloadThread(String url, int index, int startPos, int endPos, DownloadListener listener) {
		this.url = url;
		this.index = index;
		this.startPos = startPos;
		this.endPos = endPos;
		this.path = DownloadConfig.DOWNLOAD_PATH + url.substring(url.lastIndexOf("/") + 1);
		this.listener = listener;
		if (startPos == 0 && endPos == 0) {
            isSingleDownload = true;
        } else {
            isSingleDownload = false;
        }
	}

	@Override
	public void run() {
		status = DownloadEntry.DownloadStatus.downloading;
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection)new URL(url).openConnection();
			connection.setRequestMethod("GET");
			if(!isSingleDownload){
				connection.setRequestProperty("Range","bytes=" + startPos + "-" + endPos);
			}
			connection.setConnectTimeout(Constants.CONNECT_TIME);
			connection.setReadTimeout(Constants.READ_TIME);
			
			int responseCode = connection.getResponseCode();
			int contentLength = connection.getContentLength();
			File file = new File(path);
			RandomAccessFile raf = null;
			FileOutputStream fos = null;
			InputStream is = null;
			
			if(responseCode == HttpURLConnection.HTTP_PARTIAL){
				//支持断点下载
				byte[]buffer = new byte[2048];
				int len = -1;
				
				if(DownloadConfig.getInstance().getMax_download_threads() > 1){
					//子线程数>1时，使用RandomAccessFile
					Trace.d("DownloadThread==>" + "run()#####使用RandomAccessFile. Support ranges. Index:" + index + "==" + url + "***" + startPos + "-" + endPos + "**" + contentLength);
					raf = new RandomAccessFile(file, "rw");
					raf.seek(startPos);
					is = connection.getInputStream();
					
					while( (len = is.read(buffer)) != -1){
						if(isPaused || isCanceled || isError){
							break;
						}
						raf.write(buffer, 0, len);
						listener.onProgressChanged(index, len);
					}
					
					raf.close();
					is.close();
				}else{
					//子线程数为1时，使用FileOutputStream提高速度
					Trace.d("DownloadThread==>" + "run()#####使用FileOutputStream. Support ranges. Index:" + index + "==" + url + "***" + startPos + "-" + endPos + "**" + contentLength);
			        BufferedInputStream bis = null;
			        BufferedOutputStream bos = null;
			        
					if (!file.exists()) {
			            File dir = file.getParentFile();
			            if (dir.exists() || dir.mkdirs()) {
			            	file.createNewFile();
			            }
			        }
					fos = new FileOutputStream(path, true);
					bis = new BufferedInputStream(connection.getInputStream());
			        bos = new BufferedOutputStream(fos);
					
			        while( (len = bis.read(buffer)) != -1){
						if(isPaused || isCanceled || isError){
							break;
						}
						bos.write(buffer, 0, len);
						listener.onProgressChanged(index, len);
					}
			        bos.flush();
			        bis.close();
		            bos.close();
				}
				
			}else if(responseCode == HttpURLConnection.HTTP_OK){
				//不支持断点下载
				Trace.d("DownloadThread==>" + "run()#####not support ranges. Index:" + index + "==" + url + "***" + startPos + "-" + endPos + "**" + contentLength);
				fos = new FileOutputStream(file);
				is = connection.getInputStream();
				byte[]buffer = new byte[2048];
				int len = -1;
				while( (len = is.read(buffer)) != -1){
					if(isPaused || isCanceled || isError){
						break;
					}
					fos.write(buffer, 0, len);
					listener.onProgressChanged(index, len);
				}
				
				fos.close();
				is.close();
			}else{
				Trace.d("DownloadThread==>index:" + index + " run()#####server error");
				status = DownloadEntry.DownloadStatus.error;
                listener.onDownloadError(index, "server error:" + responseCode);
                return;
			}
			
			if(isPaused){
				Trace.d("DownloadThread==>index:" + index + " run()#####pause");
				status = DownloadStatus.pause;
				listener.onDownloadPaused(index);
			}else if(isCanceled){
				Trace.d("DownloadThread==>index:" + index + " run()#####cancel");
				status = DownloadStatus.cancel;
				listener.onDownloadCanceled(index);
			}else if(isError){
				Trace.d("DownloadThread==>index:" + index + " run()#####error");
				status = DownloadStatus.error;
				listener.onDownloadError(index, "cancel manually by error");
			}else{
				Trace.d("DownloadThread==>index:" + index + " run()#####done");
				status = DownloadStatus.done;
				listener.onDownloadCompleted(index);
			}
		} catch (IOException e) {
			e.printStackTrace();
			if(isPaused){
				Trace.d("DownloadThread==>" + " run()#####exception and pause");
				status = DownloadStatus.pause;
				listener.onDownloadPaused(index);
			}else if(isCanceled){
				Trace.d("DownloadThread==>index:" + index + " run()#####exception and cancel");
				status = DownloadStatus.cancel;
				listener.onDownloadCanceled(index);
			}else{
				Trace.d("DownloadThread==>index:" + index + " run()#####error");
				status = DownloadStatus.error;
				listener.onDownloadError(index, e.getMessage());
			}
			
		} finally {
			if(connection != null){
				connection.disconnect();
				Trace.d("DownloadThread==>run()#####index:" + index + "***" + url + "*****close connection");

			}
		}
	}
	
	interface DownloadListener{
		void onProgressChanged(int index, int progress);
		
		void onDownloadPaused(int index);
		
		void onDownloadCanceled(int index);

		void onDownloadCompleted(int index);
		
		void onDownloadError(int index, String message);
	}

	public void pause() {
		Trace.d("DownloadThread==>pause()#####index:" + index);
		isPaused = true;
		Thread.currentThread().interrupt();
	}
	
	public void cancel(){
		Trace.d("DownloadThread==>index:" + index + " cancel()");
		isCanceled = true;
		Thread.currentThread().interrupt();
	}

	public boolean isRunning() {
		return status == DownloadStatus.downloading;
	}

	public void cancelByError() {
		Trace.d("DownloadThread==>index:" + index + " cancelByError()");
		isError  = true;
		Thread.currentThread().interrupt();
	}

}
