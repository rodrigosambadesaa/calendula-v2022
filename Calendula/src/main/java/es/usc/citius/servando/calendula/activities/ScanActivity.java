/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.util.LogUtil;

public class ScanActivity extends CalendulaActivity {


    private static final String TAG = "ScanActivity";
    TextView textView;
    String afterScanPkg;
    String afterScanCls;
    Long patientId;
    private static final String STATE_SCAN_ACTIVE_OWNER = "scan_active_patient_owner";
    static final String EXTRA_SCAN_ACTIVE_OWNER = "scan_active_patient_owner_for_link";
    private Long activePatientOnOpen;

    /** Wrong-type restored/external extras never supply a valid scan route. */
    static String safeStringExtra(Intent source, String key) {
        try {
            return source == null ? null : source.getStringExtra(key);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    static long safeLongExtra(Intent source, String key) {
        try {
            return source == null ? -1L : source.getLongExtra(key, -1L);
        } catch (RuntimeException invalid) {
            return -1L;
        }
    }

    private boolean isSameActivePatient() {
        try {
            es.usc.citius.servando.calendula.persistence.Patient current =
                    DB.patients().getActive(this);
            return ScannedPatientLinkGuard.isSameActiveContext(
                    activePatientOnOpen, current == null ? null : current.getId());
        } catch (RuntimeException unavailableOwner) {
            // A deleted, corrupt or inaccessible active profile is never
            // permission to use a scanned link token.
            return false;
        }
    }

    public void doScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt(getString(R.string.scan_qr));
        //integrator.setResultDisplayDuration(500);
        integrator.initiateScan();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent source = getIntent();
        // The scanned token is patient-private. Do not trust redirect package
        // and class supplied in the launching intent, even inside the app.
        if (source == null || !ScannedPatientLinkGuard.isValidRoute(
                safeStringExtra(source, "after_scan_pkg"),
                safeStringExtra(source, "after_scan_cls"), getPackageName(),
                PatientDetailActivity.class.getName())) {
            finish();
            return;
        }
        patientId = safeLongExtra(source, "patient_id");
        if (!ScannedPatientLinkGuard.isValidPatientId(patientId)
                || DB.patients().findById(patientId) == null) {
            finish();
            return;
        }
        es.usc.citius.servando.calendula.persistence.Patient active =
                DB.patients().getActive(this);
        if (active == null) {
            finish();
            return;
        }
        if (savedInstanceState == null) {
            activePatientOnOpen = active.getId();
        } else {
            try {
                activePatientOnOpen = savedInstanceState.getLong(
                        STATE_SCAN_ACTIVE_OWNER, -1L);
            } catch (RuntimeException malformedRestore) {
                activePatientOnOpen = null;
            }
        }
        if (!isSameActivePatient()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_scan);
        int color = active.getColor();
        setupStatusBar(color);
        findViewById(R.id.container).setBackgroundColor(color);
        findViewById(R.id.button).setOnClickListener(v -> doScan());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (ScannedPatientLinkGuard.isValidPatientId(activePatientOnOpen)) {
            outState.putLong(STATE_SCAN_ACTIVE_OWNER, activePatientOnOpen);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        LogUtil.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IntentIntegrator.REQUEST_CODE || resultCode != RESULT_OK
                || data == null || !ScannedPatientLinkGuard.isValidPatientId(patientId)
                || DB.patients().findById(patientId) == null) {
            return;
        }
        if (!isSameActivePatient()) {
            finish();
            return;
        }

        final IntentResult result;
        try {
            result = IntentIntegrator.parseActivityResult(
                    requestCode, resultCode, data);
        } catch (RuntimeException malformedCameraResult) {
            LogUtil.w(TAG, "Rejected malformed QR scanner result");
            Toast.makeText(this, R.string.patient_qr_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        String contents = result == null ? null : result.getContents();
        if (!ScannedPatientLinkGuard.canDecode(contents)) {
            Toast.makeText(this, R.string.patient_qr_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        final String payload;
        try {
            // Android Base64 decoding may throw for malformed QR input. Bounds
            // are enforced both before decode and during GZIP inflation.
            byte[] raw = Base64.decode(contents, Base64.DEFAULT);
            payload = ScannedPatientLinkGuard.decodePayload(contents, raw);
        } catch (IllegalArgumentException | java.io.IOException malformed) {
            // Do not log the scanned bytes: they can include private tokens.
            LogUtil.w(TAG, "Rejected malformed or oversized patient QR");
            Toast.makeText(this, R.string.patient_qr_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        // Only the internal patient-detail screen may receive a patient-link
        // token. Never use arbitrary class/package values from the caller.
        // The camera can be slow. Do not forward a token into a patient
        // context that changed during scanning or decoding.
        if (!isSameActivePatient() || DB.patients().findById(patientId) == null) {
            finish();
            return;
        }
        Intent internal = new Intent(this, PatientDetailActivity.class);
        internal.putExtra("qr_data", payload);
        internal.putExtra(EXTRA_SCAN_ACTIVE_OWNER, activePatientOnOpen);
        internal.putExtra("patient_id", patientId);
        startActivity(internal);
        finish();
    }

}
