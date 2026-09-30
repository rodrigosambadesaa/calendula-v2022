package es.usc.citius.servando.calendula.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import es.usc.citius.servando.calendula.R;

/**
 * Replaces the legacy fixed status-bar top margin used by overlay toolbar titles with the
 * actual runtime system-bar/display-cutout inset without consuming insets from sibling views.
 */
public class ToolbarTitleInsetsRelativeLayout extends RelativeLayout {

    private View toolbarTitle;
    private int toolbarTitleBaseTopMargin;

    public ToolbarTitleInsetsRelativeLayout(@NonNull Context context) {
        this(context, null);
    }

    public ToolbarTitleInsetsRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarTitleInsetsRelativeLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        toolbarTitle = findViewById(R.id.toolbar_title);
        if (toolbarTitle == null) {
            return;
        }

        toolbarTitleBaseTopMargin = topMargin(toolbarTitle);
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            setTopMargin(toolbarTitle, toolbarTitleBaseTopMargin + insets.top);
            return windowInsets;
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewCompat.requestApplyInsets(this);
    }

    private static int topMargin(@NonNull View view) {
        return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin;
    }

    private static void setTopMargin(@NonNull View view, int topMargin) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin;
            view.setLayoutParams(params);
        }
    }
}
