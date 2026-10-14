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

package es.usc.citius.servando.calendula.fragments.dosePickers;


import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.Presentation;


public class DefaultDosePickerFragment extends DosePickerFragment {

    TextView unitsText;
    EditText text;
    Presentation presentation;

    @Override
    protected int getLayoutResource() {
        return R.layout.default_dose_picker;
    }

    @Override
    protected void setupRootView(View rootView) {
        unitsText = (TextView) rootView.findViewById(R.id.units_text);
        text = (EditText) rootView.findViewById(R.id.editText);
        text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable changed) {
                final Double dose = DoseInputGuard.parsePositive(changed.toString());
                // TextWatcher runs for intermediate input such as `.` and
                // empty text. Neither should crash the fragment or leave an
                // old valid unit summary beside an incomplete number.
                unitsText.setText(dose == null || presentation == null
                        ? "" : presentation.units(getResources(), dose));
                updateDoneEnabled(dose != null);
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog dialog = (AlertDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(shown -> updateDoneEnabled(
                text != null && DoseInputGuard.parsePositive(
                        text.getText().toString()) != null));
        return dialog;
    }

    private void updateDoneEnabled(boolean valid) {
        Dialog current = getDialog();
        if (current instanceof AlertDialog) {
            android.widget.Button done =
                    ((AlertDialog) current).getButton(AlertDialog.BUTTON_POSITIVE);
            if (done != null) {
                done.setEnabled(valid);
            }
        }
    }

    @Override
    protected void setInitialValue(double initialDose) {
        Bundle args = getArguments();
        Presentation p = (Presentation) args.getSerializable("presentation");
        p = p != null ? p : Presentation.UNKNOWN;
        presentation = p;
        text.setText(String.valueOf(initialDose));
    }


    @Override
    protected double getSelectedDose() {
        Double valid = DoseInputGuard.parsePositive(
                text == null ? null : text.getText().toString());
        if (valid == null) {
            // A dose may never silently become zero or non-finite.
            // The Done button is disabled for invalid input; retain a
            // fail-closed guard for programmatic calls.
            throw new IllegalArgumentException("A positive finite dose is required");
        }
        return valid;
    }

    @Override
    protected void onCancel() {
        super.onCancel();
        closeKeyboard(getActivity(), text.getWindowToken());
    }

    @Override
    protected void onDone() {
        super.onDone();
        closeKeyboard(getActivity(), text.getWindowToken());
    }
}