# GHDownload
## 一、How to use?
**Step1:**
```java 
String url = "http://gh-game.oss-cn-hangzhou.aliyuncs.com/1434794302961350.apk";
DownloadEntry entry = new DownloadEntry(url);
entry.name = "x三国.apk";
```
**Step2:**
*To start a downloading task:*
```java 
DownloadManager.getInstance(MainActivity.this).add(entry);
```
*To pausea downloading task:*
```java 
DownloadManager.getInstance(MainActivity.this).pause(entry);
```
*To resume downloading task:*
```java 
DownloadManager.getInstance(MainActivity.this).resume(entry);
```
*To resume cencel task:*
```java 
DownloadManager.getInstance(MainActivity.this).cancel(entry);
```
**Step3:**
    If you want to receive process information of downloading task, you should add observer in current Class,for example, in MainActivity:
    
*(1)Create a datawatcher to receive notification.*
```java 
       private DataWatcher dataWatcher = new DataWatcher() {

		@Override
		public void onDataChanged(DownloadEntry data) {
			entry = data;
			showText.setText(entry.toString());
		}
	};
```
*(2)Add observer.*
```java 
    @Override
    protected void onResume() {
        super.onResume();
		DownloadManager.getInstance(this).addObserver(dataWatcher);
    }
```
*(3)Remove observer.*
```java 
    @Override
    protected void onPause() {
        super.onPause();
        DownloadManager.getInstance(this).removeObserver(dataWatcher);
    }
```
## 二、Set download config
In DownloadConfig.java
You can set max downloading task and max downloading threads.

If you set max_download_threads to 1,it will use FileOutputStream instead of RandomAccessFile, which is faster. Otherwise, it will use RandomAccessFile.


```java 
private int max_download_tasks = 3;
private int max_download_threads = 3;
public static String DOWNLOAD_PATH = Environment.getExternalStorageDirectory() + File.separator +
    		"gh-download" + File.separator;
```
##备注：
（1）该下载框架使用了ormlite框架

（2）如果使用生成的ghdownloadv1.2.jar作为引用的jar包，注意要把ormlite的jar包也一起引用

（3）在AndroidManifest.xml中要添加如下service：
```java
<service android:name="com.gh.ghdownload.core.DownloadService" >
```
