package com.gh.ghdownload.db;

import java.sql.SQLException;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

import com.gh.ghdownload.entity.DownloadEntry;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

/**
 * 
 * @author shuwoom
 * @email 294299195@qq.com
 * @date 2015-9-2
 * @update 2015-9-2
 * @des OrmDBHelper
 */
public class OrmDBHelper extends OrmLiteSqliteOpenHelper{
	
	public static final String DB_NAME = "gh_downloader";
	public static final int DB_VERSION = 1;

	public OrmDBHelper(Context context, String databaseName, CursorFactory factory, int databaseVersion) {
		super(context, DB_NAME, factory, DB_VERSION);
	}

	public OrmDBHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
		try {
			TableUtils.createTable(connectionSource, DownloadEntry.class);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int arg2, int arg3) {
	}

}