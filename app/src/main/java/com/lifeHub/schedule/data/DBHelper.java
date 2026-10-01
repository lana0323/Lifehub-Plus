package com.lifeHub.schedule.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "schedule.db";
    private static final int DB_VERSION = 3;

    public static final String TABLE_EVENTS = "events";
    public static final String COL_ID = "_id";
    public static final String COL_DATE = "date";
    public static final String COL_TIME = "time";
    public static final String COL_TITLE = "title";
    public static final String COL_DESC = "description";

    public DBHelper(Context context) {
        this(context, com.lifeHub.login.AccountScope.storageName(context,DB_NAME));
    }

    public DBHelper(Context context, String name) {
        super(context, name, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_EVENTS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DATE + " TEXT NOT NULL, " +
                COL_TIME + " TEXT, " +
                COL_TITLE + " TEXT, " +
                COL_DESC + " TEXT, aiDraftId TEXT" +
                ")";
        db.execSQL(sql);
        createIndexes(db);
        createDraftIndex(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createIndexes(db);
        if (oldVersion < 3 && newVersion >= 3) {
            db.execSQL("ALTER TABLE events ADD COLUMN aiDraftId TEXT");
            createDraftIndex(db);
        }
        if (newVersion > 3) throw new IllegalStateException("Missing schedule migration");
    }

    private static void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_date_time ON events(date, time)");
    }

    private static void createDraftIndex(SQLiteDatabase db) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_events_aiDraftId ON events(aiDraftId)");
    }
    public static final class SavedEvent {
        public final Event event;
        public final boolean existing;
        SavedEvent(Event event, boolean existing) { this.event=event; this.existing=existing; }
    }
    public Event findAiDraft(String key) {
        if (key == null) return null;
        try (Cursor c=getReadableDatabase().rawQuery("SELECT _id FROM events WHERE aiDraftId=?",new String[]{key})) {
            return c.moveToFirst() ? getEventById(c.getLong(0)) : null;
        }
    }
    public SavedEvent insertEventOnce(String date, String time, String title, String desc, String key) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Event existing=findAiDraft(key);
            if(existing != null) { db.setTransactionSuccessful();return new SavedEvent(existing,true); }
            ContentValues values=new ContentValues();
            values.put(COL_DATE,date);values.put(COL_TIME,time);values.put(COL_TITLE,title);values.put(COL_DESC,desc);
            values.put("aiDraftId",key);
            long id=db.insertOrThrow(TABLE_EVENTS,null,values);
            Event saved=getEventById(id);
            if(saved==null)throw new IllegalStateException("Insert readback failed");
            db.setTransactionSuccessful();return new SavedEvent(saved,false);
        } finally { db.endTransaction(); }
    }

    public long insertEvent(String date, String time, String title, String desc) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DATE, date);
        values.put(COL_TIME, time);
        values.put(COL_TITLE, title);
        values.put(COL_DESC, desc);
        return db.insert(TABLE_EVENTS, null, values);
    }

    public List<Event> getEventsByDate(String date) {
        List<Event> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COL_DATE + " = ?";
        String[] selectionArgs = new String[]{date};
        String orderBy = COL_TIME + " ASC";

        Cursor cursor = db.query(TABLE_EVENTS, null, selection, selectionArgs,
                null, null, orderBy);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                String d = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                String t = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
                String desc = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESC));

                result.add(new Event(id, d, t, title, desc));
            }
            cursor.close();
        }
        return result;
    }

    public Event getEventById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Event result = null;

        Cursor cursor = db.query(
                TABLE_EVENTS,
                null,
                COL_ID + "=?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String d = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                String t = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
                String desc = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESC));

                result = new Event(id, d, t, title, desc);
            }
            cursor.close();
        }
        return result;
    }


    public int updateEvent(Event event) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DATE, event.getDate());
        values.put(COL_TIME, event.getTime());
        values.put(COL_TITLE, event.getTitle());
        values.put(COL_DESC, event.getDescription());

        return db.update(
                TABLE_EVENTS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(event.getId())}
        );
    }

    public int deleteEvent(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(
                TABLE_EVENTS,
                COL_ID + "=?",
                new String[]{String.valueOf(id)}
        );
    }

}
