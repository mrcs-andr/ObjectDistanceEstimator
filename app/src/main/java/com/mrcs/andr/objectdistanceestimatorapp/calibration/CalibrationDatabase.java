package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database for storing camera calibration results and application settings.
 */
@Database(entities = {CalibrationResult.class, ExtrinsicsCalibrationResult.class,
        AppSettings.class},
        version = 6, exportSchema = false)
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
                    ).fallbackToDestructiveMigration().build();
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

    /**
     * Returns the DAO for extrinsics calibration results.
     * @return ExtrinsicsCalibrationDao
     */
    public abstract ExtrinsicsCalibrationDao extrinsicsCalibrationDao();

    /**
     * Returns the DAO for application settings.
     * @return AppSettingsDao
     */
    public abstract AppSettingsDao appSettingsDao();
}
