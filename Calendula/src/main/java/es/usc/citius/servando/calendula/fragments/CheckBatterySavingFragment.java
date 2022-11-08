package es.usc.citius.servando.calendula.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.heinrichreimersoftware.materialintro.app.SlideFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.util.ManufactureBatterySavingSettings;

@RequiresApi(api = Build.VERSION_CODES.M)
public class CheckBatterySavingFragment extends SlideFragment {

//    private static Map<String, ArrayList<ManufactureBatterySavingSettings>> manufacturerMap = new HashMap<>();
//    private static List<Intent> POWERMANAGER_INTENTS = new ArrayList<>(Arrays.asList(
//
//            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
//            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
//            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
//            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
//            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
//            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
//            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
//            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")).setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:"+ CalendulaApp.getContext().getPackageName())) : null,
//            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
//            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
//            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
//            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
//            new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
//            new Intent().setComponent(new ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
//            new Intent().setComponent(new ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
//            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")),
//            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
//            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")),
//            new Intent().setComponent(new ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity")),
//            new Intent().setComponent(new ComponentName("com.dewav.dwappmanager", "com.dewav.dwappmanager.memory.SmartClearupWhiteList")),
//            new Intent().setComponent(new ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
//            new Intent().setComponent(new ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerSaverModeActivity")),
//            new Intent().setComponent(new ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity")),
//            new Intent().setComponent(new ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")).addCategory(Intent.CATEGORY_DEFAULT).putExtra("packageName", BuildConfig.APPLICATION_ID))
//    );


//    static {
//        manufacturerMap.put("huawei",new ArrayList<>());
//        manufacturerMap.get("huawei").add(
//                new ManufactureBatterySavingSettings(
//                        "huawei",
//                        Build.VERSION_CODES.N,
//                        Build.VERSION_CODES.Q,
//                        new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))));
//        manufacturerMap.get("huawei").add(
//                 new ManufactureBatterySavingSettings(
//                        "huawei",
//                        Build.VERSION_CODES.JELLY_BEAN_MR2,
//                         Build.VERSION_CODES.M,
//                        new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))));
//        manufacturerMap.put("samsung",new ArrayList<>());
//        manufacturerMap.get("samsung").add(
//                new ManufactureBatterySavingSettings(
//                        "samsung",
//                        Build.VERSION_CODES.M,
//                        Build.VERSION_CODES.Q,
//                        new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))));
//
//    }

//    private final String SKIP_INTENT_CHECK = "skipAppListMessage";
    @BindView(R.id.switch_battery_saving)
    protected Switch system_battery_saving_switch;
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

//        List<ManufactureBatterySavingSettings> manufactureBatterySavingSettings = manufacturerMap.get(Build.MANUFACTURER.toLowerCase());
//
//        if (manufactureBatterySavingSettings!=null) {
//            manufacturerOption.setVisibility(View.VISIBLE);
//            manufacturer_battery_saving_button.setOnClickListener((view1) -> {
//
//                for (Intent intent : POWERMANAGER_INTENTS) {
//                    ResolveInfo info = getContext().getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
//                    if (info != null) {
//                        try {
//                            startActivity(intent);
//                        }
//                        catch (Exception ex){
//                            Log.e("Failed:","",ex);
//                        }
////                        break;
//                    }
//                }

//                String brand = Build.BRAND.toLowerCase();
//                String manufacturer = Build.MANUFACTURER.toLowerCase();
//
//                List<PackageInfo> apps = getContext().getPackageManager().getInstalledPackages(PackageManager.GET_ACTIVITIES);
//
//                Collections.sort(apps, (a, b) -> a.packageName.compareTo(b.packageName));
//                for (PackageInfo pi : apps) {
//                    if (pi.packageName.toLowerCase().contains(brand) || pi.packageName.toLowerCase().contains(manufacturer)) {
//                        boolean print = false;
//                        StringBuilder activityInfo = new StringBuilder();
//
//                        if (pi.activities != null && pi.activities.length > 0) {
//                            List<ActivityInfo> activities = Arrays.asList(pi.activities);
//
//                            Collections.sort(activities, (a, b) -> a.name.compareTo(b.name));
//                            for (ActivityInfo ai : activities) {
//                                if (ai.name.toLowerCase().contains(brand) || ai.name.toLowerCase().contains(manufacturer)) {
//                                    activityInfo.append("  Activity: ").append(ai.name)
//                                            .append(ai.permission == null || ai.permission.length() == 0 ? "" : " - Permission: " + ai.permission)
//                                            .append("\n");
//                                    print = true;
//                                }
//                            }
//                        }
//
//                        if (print) {
//                            Log.e("brand.activities", "PackageName: " + pi.packageName);
//                            Log.w("brand.activities", activityInfo.toString());
//                        }
//                    }
//                }

//            });
//        }
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }

    @Override
    public void onResume() {
        super.onResume();
        system_battery_saving_switch.setChecked(isIgnoringBatteryOptimizations());
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        ;
        return pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }

    public boolean isAllSelected() {
        if (system_battery_saving_switch !=null && system_battery_saving_switch.isChecked()) return true;
        else return false;
    }
}
