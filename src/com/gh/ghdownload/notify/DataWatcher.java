package com.gh.ghdownload.notify;

import java.util.Observable;
import java.util.Observer;

import com.gh.ghdownload.entities.DownloadEntry;


public abstract class DataWatcher implements Observer{

	@Override
	public void update(Observable observable, Object data) {
		if(data instanceof DownloadEntry){
			onDataChanged((DownloadEntry) data);
		}
	}
	
	public abstract void onDataChanged(DownloadEntry downloadEntry);

}
