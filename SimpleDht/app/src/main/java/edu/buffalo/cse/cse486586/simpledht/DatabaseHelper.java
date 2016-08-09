package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by dhiren on 3/24/16.
 */

        import android.content.ContentValues;
        import android.content.Context;
        import android.database.Cursor;
        import android.database.sqlite.SQLiteDatabase;
        import android.database.sqlite.SQLiteOpenHelper;
        import android.util.Log;

/**
 * Created by dhiren on 2/13/16.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "messages.db";
    public static final String TABLE_NAME = "messages";
    public static final String COL_1 = "key";
    public static final String COL_2 = "value";


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null,1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table " + TABLE_NAME + " (key TEXT PRIMARY KEY, value TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public  long insert(ContentValues cv)
    {
        //  SQLiteDatabase db = this.getWritableDatabase();
       /* ContentValues cv = new ContentValues();
        cv.put(COL_1,key);
        cv.put(COL_2,value);*/
        // return db.insert(TABLE_NAME,null,cv);
       return -1;
    }

    public Cursor getData()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = db.rawQuery("select * from " + TABLE_NAME,null);
        return c;
    }
}