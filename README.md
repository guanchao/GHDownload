# GHDownload
一、How to use?
Step1:
String url = ""http://gh-game.oss-cn-hangzhou.aliyuncs.com/1434794302961350.apk"";
DownloadEntry entry = new DownloadEntry(url);
entry.name = "x三国.apk";

Step2:
To start a downloading task:
DownloadManager.getInstance(MainActivity.this).add(entry);

To pausea downloading task:
DownloadManager.getInstance(MainActivity.this).pause(entry);

To resume downloading task:
DownloadManager.getInstance(MainActivity.this).resume(entry);

To resume cencel task:
DownloadManager.getInstance(MainActivity.this).cancel(entry);

Step3:
If you want to receive process information of downloading task, you should add observer in current Class,for example, in MainActivity:
(1)Create a datawatcher to receive notification.
       private DataWatcher dataWatcher = new DataWatcher() {

		@Override
		public void onDataChanged(DownloadEntry data) {
			entry = data;
			showText.setText(entry.toString());
		}
	};

(2)Add observer.
    @Override
    protected void onResume() {
        super.onResume();
		DownloadManager.getInstance(this).addObserver(dataWatcher);
    }

(3)Remove observer.
    @Override
    protected void onPause() {
        super.onPause();
        DownloadManager.getInstance(this).removeObserver(dataWatcher);
    }
