package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * Room DAO for accessing application settings in the database.
 */
@Dao
public interface AppSettingsDao {

    /**
     * Insert or replace the settings record.
     * Because {@link AppSettings#id} is always 1, this effectively upserts a singleton row.
     *
     * @param settings the settings to save
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(AppSettings settings);

    /**
     * Retrieve the singleton settings record.
     *
     * @return the persisted settings, or null if none have been saved yet
     */
    @Query("SELECT * FROM app_settings WHERE id = 1")
    AppSettings get();
}
