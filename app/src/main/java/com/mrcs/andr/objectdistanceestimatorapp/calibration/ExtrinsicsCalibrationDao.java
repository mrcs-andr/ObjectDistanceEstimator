package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

/**
 * Room DAO for accessing extrinsics calibration results in the database.
 */
@Dao
public interface ExtrinsicsCalibrationDao {

    /**
     * Insert an extrinsics calibration result into the database.
     * @param result the extrinsics calibration result to insert
     */
    @Insert
    void insert(ExtrinsicsCalibrationResult result);

    /**
     * Retrieve the most recent extrinsics calibration result.
     * @return the latest extrinsics calibration result, or null if none exists
     */
    @Query("SELECT * FROM extrinsics_results ORDER BY timestamp DESC LIMIT 1")
    ExtrinsicsCalibrationResult getLatest();
}
