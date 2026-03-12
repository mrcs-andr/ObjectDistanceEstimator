package com.mrcs.andr.objectdistanceestimatorapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mrcs.andr.objectdistanceestimatorapp.calibration.AppSettings;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationDatabase;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationResult;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.ExtrinsicsCalibrationResult;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity that displays the most recently saved camera intrinsics and extrinsics calibration
 * results, and allows the user to configure the alert distance setting.
 */
public class IntrinsicsViewActivity extends AppCompatActivity {

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    /**
     * On Create method for the IntrinsicsViewActivity. Sets the content view and
     * loads the latest calibration results and settings from the database.
     * @param savedInstanceState The saved instance state bundle, if any.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intrinsics_view);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dbExecutor.execute(() -> {
            CalibrationDatabase db = CalibrationDatabase.getInstance(getApplicationContext());
            CalibrationResult result = db.calibrationDao().getLatest();
            ExtrinsicsCalibrationResult extrinsics = db.extrinsicsCalibrationDao().getLatest();
            AppSettings settings = db.appSettingsDao().get();
            runOnUiThread(() -> {
                populateViews(result);
                populateExtrinsicsViews(extrinsics);
                populateAlertDistance(settings);
            });
        });

        Button btnSave = findViewById(R.id.btnSaveAlertDistance);
        btnSave.setOnClickListener(v -> onSaveAlertDistanceClicked());
    }

    /**
     * Populates all TextViews with the values from the given calibration result.
     * @param result the latest calibration result, or null if none is available.
     */
    private void populateViews(CalibrationResult result) {
        if (result == null) {
            return;
        }

        TextView tvTimestamp = findViewById(R.id.tvIntrinsicsTimestamp);
        TextView tvFx = findViewById(R.id.tvFx);
        TextView tvFy = findViewById(R.id.tvFy);
        TextView tvCx = findViewById(R.id.tvCx);
        TextView tvCy = findViewById(R.id.tvCy);
        TextView tvK1 = findViewById(R.id.tvK1);
        TextView tvK2 = findViewById(R.id.tvK2);
        TextView tvK3 = findViewById(R.id.tvK3);
        TextView tvP1 = findViewById(R.id.tvP1);
        TextView tvP2 = findViewById(R.id.tvP2);
        TextView tvRmsError = findViewById(R.id.tvRmsError);

        String dateStr = DateFormat.getDateTimeInstance().format(new Date(result.timestamp));
        tvTimestamp.setText(getString(R.string.intrinsics_view_timestamp, dateStr));
        tvFx.setText(String.format("%.4f", result.fx));
        tvFy.setText(String.format("%.4f", result.fy));
        tvCx.setText(String.format("%.4f", result.cx));
        tvCy.setText(String.format("%.4f", result.cy));
        tvK1.setText(String.format("%.6f", result.k1));
        tvK2.setText(String.format("%.6f", result.k2));
        tvK3.setText(String.format("%.6f", result.k3));
        tvP1.setText(String.format("%.6f", result.p1));
        tvP2.setText(String.format("%.6f", result.p2));
        tvRmsError.setText(String.format("%.6f", result.rmsError));
    }

    /**
     * Populates the extrinsics (camera pose) TextViews with the given result.
     * If no result is available the placeholder message remains visible.
     * @param extrinsics the latest extrinsics calibration result, or null if none is available.
     */
    private void populateExtrinsicsViews(ExtrinsicsCalibrationResult extrinsics) {
        TextView tvExtrinsicsTimestamp = findViewById(R.id.tvExtrinsicsTimestamp);
        View layoutExtrinsicsValues = findViewById(R.id.layoutExtrinsicsValues);

        if (extrinsics == null) {
            tvExtrinsicsTimestamp.setText(R.string.intrinsics_view_extrinsics_none);
            layoutExtrinsicsValues.setVisibility(View.GONE);
            return;
        }

        String dateStr = DateFormat.getDateTimeInstance().format(new Date(extrinsics.timestamp));
        tvExtrinsicsTimestamp.setText(getString(R.string.intrinsics_view_extrinsics_timestamp,
                dateStr));
        layoutExtrinsicsValues.setVisibility(View.VISIBLE);

        TextView tvCameraX = findViewById(R.id.tvCameraX);
        TextView tvCameraY = findViewById(R.id.tvCameraY);
        TextView tvCameraZ = findViewById(R.id.tvCameraZ);
        TextView tvCameraYaw = findViewById(R.id.tvCameraYaw);
        TextView tvCameraPitch = findViewById(R.id.tvCameraPitch);
        TextView tvCameraRoll = findViewById(R.id.tvCameraRoll);

        tvCameraX.setText(String.format("%.4f", extrinsics.cameraX));
        tvCameraY.setText(String.format("%.4f", extrinsics.cameraY));
        tvCameraZ.setText(String.format("%.4f", extrinsics.cameraZ));
        tvCameraYaw.setText(String.format("%.2f", extrinsics.cameraYaw));
        tvCameraPitch.setText(String.format("%.2f", extrinsics.cameraPitch));
        tvCameraRoll.setText(String.format("%.2f", extrinsics.cameraRoll));
    }

    /**
     * Populates the alert distance field with the stored value.
     * Falls back to the default ({@link AppSettings#DEFAULT_ALERT_DISTANCE} m) if no settings
     * record exists yet.
     * @param settings the persisted settings, or null if none exist.
     */
    private void populateAlertDistance(AppSettings settings) {
        EditText etAlertDistance = findViewById(R.id.etAlertDistance);
        double distance = (settings != null) ? settings.alertDistance : AppSettings.DEFAULT_ALERT_DISTANCE;
        etAlertDistance.setText(String.valueOf(distance));
    }

    /**
     * Validates the alert distance input and persists it to the database.
     */
    private void onSaveAlertDistanceClicked() {
        EditText etAlertDistance = findViewById(R.id.etAlertDistance);
        String input = etAlertDistance.getText().toString().trim();
        double distance;
        try {
            distance = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.intrinsics_view_alert_distance_invalid,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (distance < 0) {
            Toast.makeText(this, R.string.intrinsics_view_alert_distance_invalid,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        AppSettings settings = new AppSettings();
        settings.alertDistance = distance;
        dbExecutor.execute(() -> {
            CalibrationDatabase.getInstance(getApplicationContext())
                    .appSettingsDao().save(settings);
            runOnUiThread(() -> Toast.makeText(
                    this, R.string.intrinsics_view_alert_distance_saved,
                    Toast.LENGTH_SHORT).show());
        });
    }

    /**
     * On Destroy Activity lifecycle event.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }

    /**
     * Handle the Up/back button in the action bar.
     * @return true if handled.
     */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
