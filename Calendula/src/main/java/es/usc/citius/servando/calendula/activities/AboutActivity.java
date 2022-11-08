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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.mikepenz.aboutlibraries.LibsConfiguration;
import com.mikepenz.aboutlibraries.entity.Library;
import com.mikepenz.aboutlibraries.ui.item.HeaderItem;
import com.mikepenz.aboutlibraries.ui.item.LibraryItem;
import com.mikepenz.aboutlibraries.util.MovementCheck;


import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class AboutActivity extends CalendulaActivity {

    @BindView(R.id.aboutDescription)
    TextView description;

    @BindView(R.id.aboutMoreInfo)
    TextView moreInfo;

    @BindView(R.id.aboutPrivacy)
    TextView privacy;

    @BindView(R.id.aboutName)
    TextView name;

//    @BindView(R.id.aboutIcon)
//    ImageView appIcon;

    @BindView(R.id.aboutLibs)
    TextView aboutLibs;

    @BindView(R.id.aboutVersion)
    TextView aboutVersion;

    @BindView(R.id.aboutLogoCITIUS)
    ImageView logoCITIUS;

    @BindView(R.id.aboutLogoUSC)
    ImageView logoUSC;

    @BindView(R.id.aboutLogoCITIUS2)
    ImageView logoCITIUS2;

    @BindView(R.id.aboutLogoUSC2)
    ImageView logoUSC2;


    private static final String[] libraryNames = new String[]{
//            "Butter Knife",
//            "EventBus",
//            "Jsoup",
//            "OkHttp",
//            "Picasso",
//            "Retrofit",
//            "BetterPickers",
            "Android-Job",
//            "Joda-Time",
//            "Caldroid",
//            "AppAuth-Android",
            "Apache Commons IO",
//            "Gson",
//            "ZXing",
            "Android Material Intro Screen"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ButterKnife.bind(this);
        setupToolbar(getString(R.string.title_about), getResources().getColor(R.color.dark_grey_home));
        setupStatusBar(getResources().getColor(R.color.dark_grey_home));



            name.setText(getString(R.string.app_name));
            description.setText(Html.fromHtml(getString(R.string.about_main_description)));
            moreInfo.setText(Html.fromHtml(String.format(getString(R.string.about_more_info), PreferenceUtils.getString(PreferenceKeys.MORE_INFO_URL, getString(R.string.about_more_info_url)))));
            moreInfo.setMovementMethod(MovementCheck.getInstance());
            privacy.setText(Html.fromHtml(String.format(getString(R.string.about_privacy), PreferenceUtils.getString(PreferenceKeys.PRIVACY_URL, getString(R.string.about_privacy_url)))));
            privacy.setMovementMethod(MovementCheck.getInstance());
            aboutLibs.setText(getString(R.string.about_description));

            //get the packageManager to load and read some values :D
            PackageManager pm = getPackageManager();
            //get the packageName
            String packageName = getPackageName();
            //Try to load the applicationInfo
            ApplicationInfo appInfo = null;
            PackageInfo packageInfo = null;
//            Drawable icon = null;
            try {
                appInfo = pm.getApplicationInfo(packageName, 0);
                packageInfo = pm.getPackageInfo(packageName, 0);
            } catch (Exception ex) {
            }

//            icon = appInfo.loadIcon(pm);
//            appIcon.setImageDrawable(icon);

            String versionName = null;
            Integer versionCode = null;
            if (packageInfo != null) {
                versionName = packageInfo.versionName;
                versionCode = packageInfo.versionCode;
                if (versionName != null && versionCode != null) {
                    aboutVersion.setText(getString(R.string.version) + " " + versionName + " (" + versionCode + ")");
                } else {
                    if (versionName != null) {
                        aboutVersion.setText(getString(R.string.version) + " " + versionName);
                    } else if (versionCode != null) {
                        aboutVersion.setText(getString(R.string.version) + " " + versionCode);
                    } else {
                        aboutVersion.setVisibility(View.GONE);
                    }
                }
            }

            logoCITIUS.setImageResource(R.drawable.ic_logo_citius_2019_54px);
            logoUSC.setImageResource(R.drawable.ic_logousc_54px);
            logoCITIUS2.setImageResource(R.drawable.ic_logo_citius_2019_54px);
            logoUSC2.setImageResource(R.drawable.ic_logousc_54px);

        if (savedInstanceState == null) {
            Fragment fragment = new LibsBuilder()
//                    .withAboutAppName(getString(R.string.app_name))
//                    .withAboutIconShown(false)
//                    .withAboutVersionShown(false)
                    .withLicenseShown(true)
                    .withLicenseDialog(true)
                    .withFields(R.string.class.getFields())
//                    .withAboutDescription(getString(R.string.about_description))
                    .withLibraries(libraryNames)
                    .withLibsRecyclerViewListener(new LibsConfiguration.LibsRecyclerViewListener() {
                        @Override
                        public void onBindViewHolder(HeaderItem.ViewHolder headerViewHolder) {

                        }

                        @Override
                        public void onBindViewHolder(LibraryItem.ViewHolder viewHolder) {
                            viewHolder.itemView.findViewById(R.id.libraryDescription).setVisibility(View.GONE);
                            viewHolder.itemView.findViewById(R.id.libraryBottomDivider).setVisibility(View.GONE);
                        }
                    })
                    .withListener(new LibsConfiguration.LibsListener() {
                        @Override
                        public void onIconClicked(View v) {
                        }

                        @Override
                        public boolean onLibraryAuthorClicked(View v, Library library) {
                            return true;
                        }

                        @Override
                        public boolean onLibraryContentClicked(View v, Library library) {
                            return true;
                        }

                        @Override
                        public boolean onLibraryBottomClicked(View v, Library library) {
                            return false;
                        }

                        @Override
                        public boolean onExtraClicked(View v, Libs.SpecialButton specialButton) {
                            return true;
                        }

                        @Override
                        public boolean onIconLongClicked(View v) {
                            return true;
                        }

                        @Override
                        public boolean onLibraryAuthorLongClicked(View v, Library library) {
                            return true;
                        }

                        @Override
                        public boolean onLibraryContentLongClicked(View v, Library library) {
                            return true;
                        }

                        @Override
                        public boolean onLibraryBottomLongClicked(View v, Library library) {
                            return false;
                        }
                    })
                    .supportFragment();

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.fragment_holder, fragment).commit();
        }
    }

}
