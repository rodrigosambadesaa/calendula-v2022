package es.usc.citius.servando.calendula.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.heinrichreimersoftware.materialintro.app.SlideFragment;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import es.usc.citius.servando.calendula.R;

public class CheckBatterySavingFragment extends SlideFragment {

    @BindView(R.id.switch_battery_saving)
    protected SwitchCompat system_battery_saving_switch;
    @BindView(R.id.manufacturer_battery_saving_button)
    protected Button manufacturer_battery_saving_button;
    @BindView(R.id.manufacturer_battery_saving_option)
    protected LinearLayout manufacturerOption;
    private Unbinder unbinder;

    public CheckBatterySavingFragment() {
        // Required empty public constructor
    }

    public static CheckBatterySavingFragment newInstance() {
        return new CheckBatterySavingFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_disable_battery_saving, container, false);
        unbinder = ButterKnife.bind(this, view);

        system_battery_saving_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != isIgnoringBatteryOptimizations()) {
                if (isChecked) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                    startActivity(intent);
                } else {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null) {
            unbinder.unbind();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        system_battery_saving_switch.setChecked(isIgnoringBatteryOptimizations());
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }

    public boolean isAllSelected() {
        return system_battery_saving_switch != null && system_battery_saving_switch.isChecked();
    }
}
