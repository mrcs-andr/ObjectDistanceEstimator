package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Room DAO for accessing calibration results in the database.
 */
@Dao
public interface CalibrationDao {

    /**
     * Insert a calibration result into the database.
     * @param result the calibration result to insert
     */
    @Insert
    void insert(CalibrationResult result);

    /**
     * Retrieve all calibration results ordered by timestamp descending.
     * @return list of all calibration results
     */
    @Query("SELECT * FROM calibration_results ORDER BY timestamp DESC")
    List<CalibrationResult> getAll();

    /**
     * Retrieve the most recent calibration result.
     * @return the latest calibration result, or null if none exists
     */
    @Query("SELECT * FROM calibration_results ORDER BY timestamp DESC LIMIT 1")
    CalibrationResult getLatest();
}
