package com.mrcs.andr.objectdistanceestimatorapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationDatabase;
import com.mrcs.andr.objectdistanceestimatorapp.calibration.CalibrationResult;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity that displays the most recently saved camera intrinsics calibration result.
 */
public class IntrinsicsViewActivity extends AppCompatActivity {

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    /**
     * On Create method for the IntrinsicsViewActivity. Sets the content view and
     * loads the latest calibration result from the database.
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
            CalibrationResult result = CalibrationDatabase.getInstance(getApplicationContext())
                    .calibrationDao().getLatest();
            runOnUiThread(() -> populateViews(result));
        });
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
