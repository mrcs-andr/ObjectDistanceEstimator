package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room database for storing camera calibration results.
 */
@Database(entities = {CalibrationResult.class}, version = 1, exportSchema = false)
public abstract class CalibrationDatabase extends RoomDatabase {

    private static final String DB_NAME = "calibration_db";
    private static volatile CalibrationDatabase instance;

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
                    ).build();
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
