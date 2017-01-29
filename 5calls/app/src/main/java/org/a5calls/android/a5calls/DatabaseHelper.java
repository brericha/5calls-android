package org.a5calls.android.a5calls;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Local database helper. I believe this is already "thread-safe" and such because SQLiteOpenHelper
 * handles all of that for us. As long as we just use one SQLiteOpenHelper from AppSingleton
 * we should be safe!
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final int DATABASE_VERSION = 1;
    private static final String CALLS_TABLE_NAME = "UserCallsDatabase";

    private static class CallsColumns {
        public static String TIMESTAMP = "timestamp";
        public static String CONTACT_ID = "contactid";
        public static String ISSUE_ID = "issueid";
        public static String LOCATION = "location";
        public static String RESULT = "result";
    }

    private static final String CALLS_TABLE_CREATE =
            "CREATE TABLE " + CALLS_TABLE_NAME + " (" +
                CallsColumns.TIMESTAMP + " INTEGER, " + CallsColumns.CONTACT_ID + " STRING, " +
                    CallsColumns.ISSUE_ID + " STRING, " + CallsColumns.LOCATION + " STRING, " +
                    CallsColumns.RESULT + " STRING);";


    DatabaseHelper(Context context) {
        super(context, CALLS_TABLE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CALLS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /**
     * Adds a successful call to the user's local database
     * @param issueId
     * @param contactId
     * @param location
     * @param result
     */
    public void addCall(String issueId, String contactId, String location, String result) {
        ContentValues values = new ContentValues();
        values.put(CallsColumns.TIMESTAMP, System.currentTimeMillis());
        values.put(CallsColumns.CONTACT_ID, contactId);
        values.put(CallsColumns.ISSUE_ID, issueId);
        values.put(CallsColumns.LOCATION, location);
        values.put(CallsColumns.RESULT, result);
        getWritableDatabase().insert(CALLS_TABLE_NAME, null, values);
    }

    /**
     * Gets the calls in the database for a particular issue and contact.
     * @param issueId
     * @param contactId
     */
    public void getCalls(String issueId, String contactId) {
        String[] columns = {CallsColumns.TIMESTAMP, CallsColumns.RESULT};

    }

    /**
     * Gets the total number of calls this user has made
     */
    public int getCallsCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + CallsColumns.TIMESTAMP + " FROM " + CALLS_TABLE_NAME, null);
        int count = c.getCount();
        c.close();
        return count;
    }
}
