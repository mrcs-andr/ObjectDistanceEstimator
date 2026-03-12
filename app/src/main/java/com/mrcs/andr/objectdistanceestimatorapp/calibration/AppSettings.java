package com.mrcs.andr.objectdistanceestimatorapp.calibration;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing application-level settings that need to be persisted
 * across app restarts. A single row with id=1 is used as a singleton settings record.
 */
@Entity(tableName = "app_settings")
public class AppSettings {

    /** Default alert distance in metres. */
    public static final double DEFAULT_ALERT_DISTANCE = 30.0;

    /** Fixed primary key so only one settings row ever exists. */
    @PrimaryKey
    public int id = 1;

    /**
     * Distance (in metres) at which the app should alert the user that an object is near.
     * Default value is {@value DEFAULT_ALERT_DISTANCE} metres.
     */
    public double alertDistance = DEFAULT_ALERT_DISTANCE;
}
