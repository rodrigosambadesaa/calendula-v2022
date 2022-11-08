package es.usc.citius.servando.calendula.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.os.LocaleList;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.materialdrawer.model.AbstractDrawerItem;

import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;

/**
 * Utility to change app locale on the fly
 * Based on https://gist.github.com/gunhansancar/45648176dc47d50b1940/
 */
public class LocaleHelper {


    public static String getLanguage(Context context) {
        String defaultLang = Locale.getDefault().getLanguage();
        return getLanguage(context, defaultLang);
    }

    public static String getLanguage(Context context, String defaultLang) {
        return PreferenceUtils.getString(PreferenceKeys.SETTINGS_PREFERRED_LANG, defaultLang);
    }

    public static String getLocaleName(Context c, String locale) {
        if (c.getString(R.string.locale_gl_value).equals(locale)) {
            return c.getString(R.string.pref_locale_gl);
        } else if (c.getString(R.string.locale_es_value).equals(locale)) {
            return c.getString(R.string.pref_locale_es);
        } else if (c.getString(R.string.locale_en_value).equals(locale)) {
            return c.getString(R.string.pref_locale_en);
        } else {
            return c.getString(R.string.unknown);
        }
    }

    public static void updateLocaleFromActivity(final Activity context, final String lang) {

        new MaterialStyledDialog.Builder(context)
                .setTitle(R.string.language_switch_title)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(context, CommunityMaterial.Icon.cmd_earth, R.color.white, 48))
                .setHeaderColorInt(DB.patients().getActive(context).getColor())
                .withDialogAnimation(false)
                .setDescription(context.getString(R.string.language_switch_message, getLocaleName(context, lang)))
                .setPositiveText(R.string.ok)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                        PreferenceUtils.edit().putString(PreferenceKeys.SETTINGS_PREFERRED_LANG.key(), lang).commit();
                        context.recreate();
                    }
                })
                .setNegativeText(R.string.cancel)
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    public static Context setLocale(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        context = context.createConfigurationContext(configuration);
        return context;
    }


    public static class LangDrawerItem extends AbstractDrawerItem<LangDrawerItem, LangDrawerItem.ViewHolder> {

        Activity activity;
        String localeGL;
        String localeES;
        String localeEN;

        public LangDrawerItem(Activity activity) {
            this.activity = activity;
            localeGL = activity.getString(R.string.locale_gl_value);
            localeES = activity.getString(R.string.locale_es_value);
            localeEN = activity.getString(R.string.locale_en_value);
        }

        @Override
        public int getType() {
            return R.id.material_drawer_lang_drawer_item;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.lang_drawer_item;
        }

        @Override
        public ViewHolder getViewHolder(View v) {
            return new ViewHolder(v);
        }

        @Override
        public void unbindView(ViewHolder holder) {
            super.unbindView(holder);
        }

        @Override
        public void bindView(ViewHolder holder, List<Object> payloads) {
            super.bindView(holder, payloads);

            final Context ctx = holder.itemView.getContext();
            final ViewHolder viewHolder = (ViewHolder) holder;
            final String current = LocaleHelper.getLanguage(ctx);
            viewHolder.itemView.setId(hashCode());
            updateSelectedLangView(ctx, viewHolder);

            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String selected = current;
                    switch (v.getId()) {
                        case R.id.lang_en:
                            selected = localeEN;
                            break;
                        case R.id.lang_es:
                            selected = localeES;
                            break;
                        case R.id.lang_gl:
                            selected = localeGL;
                            break;
                    }
                    if (!selected.equals(current)) {
                        LocaleHelper.updateLocaleFromActivity(activity, selected);
                    }
                }
            };
            viewHolder.en.setOnClickListener(listener);
            viewHolder.es.setOnClickListener(listener);
            viewHolder.gl.setOnClickListener(listener);
        }

        void updateSelectedLangView(Context ctx, ViewHolder viewHolder) {
            String lang = LocaleHelper.getLanguage(ctx);
            int selected = ctx.getResources().getColor(R.color.black);
            int unselected = ctx.getResources().getColor(R.color.black_40);

            viewHolder.en.setTextColor(unselected);
            viewHolder.es.setTextColor(unselected);
            viewHolder.gl.setTextColor(unselected);

            if (localeEN.equals(lang)) {
                viewHolder.en.setTextColor(selected);
            } else if (localeES.equals(lang)) {
                viewHolder.es.setTextColor(selected);
            }
            if (localeGL.equals(lang)) {
                viewHolder.gl.setTextColor(selected);
            }
        }

        @Override
        public boolean isAutoExpanding() {
            return false;
        }


        public class ViewHolder extends RecyclerView.ViewHolder {

            @BindView(R.id.lang_en)
            public TextView en;
            @BindView(R.id.lang_es)
            public TextView es;
            @BindView(R.id.lang_gl)
            public TextView gl;

            private ViewHolder(View view) {
                super(view);
                ButterKnife.bind(this, view);
            }
        }
    }
}