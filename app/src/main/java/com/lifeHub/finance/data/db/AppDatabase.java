package com.lifeHub.finance.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import android.database.Cursor;

import com.lifeHub.finance.data.model.ExpenseEntity;

@Database(
        entities = {
                ExpenseEntity.class
        },
        version = 5,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            boolean hasAccountType = false;
            try (Cursor cursor = db.query("PRAGMA table_info(expenses)")) {
                while (cursor.moveToNext()) {
                    if ("account_type".equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                        hasAccountType = true;
                    }
                }
            }
            if (!hasAccountType) db.execSQL("ALTER TABLE expenses ADD COLUMN account_type TEXT");
        }
    };
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE INDEX index_expenses_timeMillis ON expenses(timeMillis)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN aiDraftId TEXT");
            db.execSQL("CREATE UNIQUE INDEX index_expenses_aiDraftId ON expenses(aiDraftId)");
        }
    };
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            // Room runs migrations in a transaction. Failure leaves the original v4 database intact.
            db.execSQL("CREATE TABLE expenses_minor (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountMinor INTEGER NOT NULL, legacyAmount REAL, aiDraftId TEXT, category TEXT, note TEXT, timeMillis INTEGER NOT NULL, account_type TEXT)");
            long previousSequence=0;
            try(Cursor sequence=db.query("SELECT seq FROM sqlite_sequence WHERE name='expenses'")) {
                if(sequence.moveToFirst())previousSequence=sequence.getLong(0);
            }
            java.math.BigInteger expected=java.math.BigInteger.ZERO;
            long count=0;
            try(Cursor rows=db.query("SELECT id,amount,aiDraftId,category,note,timeMillis,account_type FROM expenses")) {
                while(rows.moveToNext()) {
                    if(rows.isNull(1))throw new IllegalStateException("Missing legacy amount");
                    double original=rows.getDouble(1);
                    long minor=com.lifeHub.finance.domain.Money.fromLegacy(original);
                    expected=expected.add(java.math.BigInteger.valueOf(minor));count++;
                    db.execSQL("INSERT INTO expenses_minor(id,amountMinor,legacyAmount,aiDraftId,category,note,timeMillis,account_type) VALUES(?,?,?,?,?,?,?,?)",
                            new Object[]{rows.getLong(0),minor,original,rows.getString(2),rows.getString(3),rows.getString(4),rows.getLong(5),rows.getString(6)});
                }
            }
            java.math.BigInteger actual=java.math.BigInteger.ZERO;long copied=0;
            try(Cursor rows=db.query("SELECT amountMinor FROM expenses_minor")) {
                while(rows.moveToNext()){actual=actual.add(java.math.BigInteger.valueOf(rows.getLong(0)));copied++;}
            }
            if(count!=copied || !expected.equals(actual))throw new IllegalStateException("Money migration verification failed");
            db.execSQL("DROP TABLE expenses");
            db.execSQL("ALTER TABLE expenses_minor RENAME TO expenses");
            db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name='expenses'",new Object[]{previousSequence});
            db.execSQL("INSERT INTO sqlite_sequence(name,seq) SELECT 'expenses',? WHERE NOT EXISTS(SELECT 1 FROM sqlite_sequence WHERE name='expenses')",new Object[]{previousSequence});
            db.execSQL("CREATE INDEX index_expenses_timeMillis ON expenses(timeMillis)");
            db.execSQL("CREATE UNIQUE INDEX index_expenses_aiDraftId ON expenses(aiDraftId)");
        }
    };
    private static final java.util.Map<String,AppDatabase> INSTANCES=new java.util.HashMap<>();
    public abstract ExpenseDao expenseDao();
    public static synchronized AppDatabase getInstance(Context context) {
        String name=com.lifeHub.login.AccountScope.storageName(context,"finance.db");
        AppDatabase db=INSTANCES.get(name);
        if(db==null) {
            db=Room.databaseBuilder(context.getApplicationContext(),AppDatabase.class,name)
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build();
            INSTANCES.put(name,db);
        }
        return db;
    }
}
