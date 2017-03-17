package com.example.lion.takephoto;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class CountHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME="counter.db";

    public CountHelper(Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE counter (key INTEGER, value INTEGER);");
        db.execSQL("INSERT INTO counter VALUES(0, 0);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    protected int assign() {
        int count;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("counter", new String[] {"value"}, "key=?", new String[] {"0"}, null, null, null);
        cursor.moveToNext();
        count = cursor.getInt(cursor.getColumnIndex("value"));
        count++;
        db = this.getWritableDatabase();
        db.execSQL("UPDATE counter SET value="+String.valueOf(count)+" WHERE key=0;");
        Log.i("Counter", "Assign: "+String.valueOf(count));
        return count;
    }
}
