package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database for storing camera calibration results.
 */
@Database(entities = {CalibrationResult.class}, version = 2, exportSchema = false)
public abstract class CalibrationDatabase extends RoomDatabase {

    private static final String DB_NAME = "calibration_db";
    private static volatile CalibrationDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE calibration_results ADD COLUMN cameraHeight REAL NOT NULL DEFAULT 1.5");
            database.execSQL(
                    "ALTER TABLE calibration_results ADD COLUMN cameraPitch REAL NOT NULL DEFAULT 0.0");
        }
    };

    /**
     * Get the singleton database instance.
     * @param context application context
     * @return CalibrationDatabase instance
     */
    public static CalibrationDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (CalibrationDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            CalibrationDatabase.class,
                            DB_NAME
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return instance;
    }

    /**
     * Returns the DAO for calibration results.
     * @return CalibrationDao
     */
    public abstract CalibrationDao calibrationDao();
}
