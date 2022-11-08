package es.usc.citius.servando.calendula.activities;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.viewpager.widget.ViewPager;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.heinrichreimersoftware.materialintro.app.IntroActivity;
import com.heinrichreimersoftware.materialintro.slide.FragmentSlide;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.fragments.CheckBatterySavingFragment;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;


@RequiresApi(api = Build.VERSION_CODES.M)
public class CheckBatterySavingActivity extends IntroActivity {

    private static final int IGNORE_OPTIMIZATION_REQUEST = 1;
    private int location = 0;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        setFullscreen(true);
        super.onCreate(savedInstanceState);

        setButtonBackFunction(BUTTON_BACK_FUNCTION_SKIP);

        addSlide(new FragmentSlide.Builder()
                .background(R.color.healthcare_provider_light)
                .backgroundDark(R.color.healthcare_provider_dark)
                .fragment(CheckBatterySavingFragment.newInstance())
                .build());

        addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                location = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceUtils.edit().putBoolean(PreferenceKeys.HOME_FIRST_SETTINGS_SHOWN.key(), true).apply();
    }

    @Override
    public void nextSlide() {
        CheckBatterySavingFragment fragment = (CheckBatterySavingFragment) getSlide(location).getFragment();
        if (!fragment.isAllSelected()) {
            new MaterialStyledDialog.Builder(this)
                    .setTitle(R.string.battery_saving_dialog_title)
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_alert, R.color.md_yellow_700, 48))
                    .setHeaderColor(R.color.healthcare_provider_dark)
                    .withDialogAnimation(false)
                    .setDescription(R.string.battery_saving_dialog_text)
                    .setPositiveText(getString(R.string.battery_saving_dialog_ok))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            CheckBatterySavingActivity.super.nextSlide();
                        }
                    })
                    .setNegativeText(getString(R.string.battery_saving_dialog_cancel))
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.cancel();
                        }
                    })
                    .show();
        }
        else super.nextSlide();
    }


}
