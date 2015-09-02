package com.gh.ghdownload.test;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.gh.ghdownload.DownloadManager;
import com.gh.ghdownload.R;
import com.gh.ghdownload.entities.DownloadEntry;
import com.gh.ghdownload.notify.DataWatcher;
import com.gh.ghdownload.utilities.FileUtils;
import com.gh.ghdownload.utilities.Trace;

public class MainActivity extends Activity implements OnClickListener{
	
	private DownloadEntry entry = new DownloadEntry();
	private String url = "http://gh-game.oss-cn-hangzhou.aliyuncs.com/1434794302961350.apk";
//	private String url = "http://dldir1.qq.com/foxmail/weixin42s60v3.sisx";
	
	private Button addBtn;
	private Button cancelBtn;
	private Button pauseBtn;
	private Button resumeBtn;
	
	private TextView showText;
	
	private DataWatcher dataWatcher = new DataWatcher() {

		@Override
		public void onDataChanged(DownloadEntry data) {
			entry = data;
			showText.setText(entry.toString());
//			Trace.d(entry.toString());
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		entry.name = "x三国.apk";
//		entry.name = "weixin42s60v3.sisx";
		entry.url = url;
		
		showText = (TextView)findViewById(R.id.show_text);
		addBtn = (Button)findViewById(R.id.add_btn);
		cancelBtn = (Button)findViewById(R.id.cancel_btn);
		pauseBtn = (Button)findViewById(R.id.pause_btn);
		resumeBtn = (Button)findViewById(R.id.resume_btn);
		
		addBtn.setOnClickListener(this);
		cancelBtn.setOnClickListener(this);
		pauseBtn.setOnClickListener(this);
		resumeBtn.setOnClickListener(this);
		
	}
	
	@Override
    protected void onResume() {
        super.onResume();
		DownloadManager.getInstance(this).addObserver(dataWatcher);
    }
	
	@Override
    protected void onPause() {
        super.onPause();
        DownloadManager.getInstance(this).removeObserver(dataWatcher);
    }

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.add_btn:
			DownloadManager.getInstance(MainActivity.this).add(entry);
			break;
		case R.id.pause_btn:
			DownloadManager.getInstance(MainActivity.this).pause(entry);
			break;
		case R.id.resume_btn:
			DownloadManager.getInstance(MainActivity.this).resume(entry);
			break;
		case R.id.cancel_btn:
			DownloadManager.getInstance(MainActivity.this).cancel(entry);
			break;
		}
	}

}
