package es.usc.citius.servando.calendula.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import es.usc.citius.servando.calendula.R;

/**
 * Keeps PatientDetail's blue header edge-to-edge while replacing its legacy +/-24dp
 * status-bar compensation with the actual runtime inset.
 */
public class PatientDetailInsetsRelativeLayout extends RelativeLayout {

    private View toolbar;
    private ScrollView scroll;
    private GridView grid;
    private int toolbarBaseTopMargin;
    private int scrollBaseTopMargin;
    private int toolbarPaddingLeft;
    private int toolbarPaddingTop;
    private int toolbarPaddingRight;
    private int toolbarPaddingBottom;
    private int scrollPaddingLeft;
    private int scrollPaddingTop;
    private int scrollPaddingRight;
    private int scrollPaddingBottom;
    private int gridPaddingLeft;
    private int gridPaddingTop;
    private int gridPaddingRight;
    private int gridPaddingBottom;

    public PatientDetailInsetsRelativeLayout(@NonNull Context context) {
        this(context, null);
    }

    public PatientDetailInsetsRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PatientDetailInsetsRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        toolbar = findViewById(R.id.toolbar);
        scroll = findViewById(R.id.scroll);
        grid = findViewById(R.id.grid);

        toolbarBaseTopMargin = topMargin(toolbar);
        scrollBaseTopMargin = topMargin(scroll);
        toolbarPaddingLeft = toolbar.getPaddingLeft();
        toolbarPaddingTop = toolbar.getPaddingTop();
        toolbarPaddingRight = toolbar.getPaddingRight();
        toolbarPaddingBottom = toolbar.getPaddingBottom();
        scrollPaddingLeft = scroll.getPaddingLeft();
        scrollPaddingTop = scroll.getPaddingTop();
        scrollPaddingRight = scroll.getPaddingRight();
        scrollPaddingBottom = scroll.getPaddingBottom();
        gridPaddingLeft = grid.getPaddingLeft();
        gridPaddingTop = grid.getPaddingTop();
        gridPaddingRight = grid.getPaddingRight();
        gridPaddingBottom = grid.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(this, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            setTopMargin(toolbar, toolbarBaseTopMargin + insets.top);
            setTopMargin(scroll, scrollBaseTopMargin - insets.top);

            toolbar.setPadding(
                    toolbarPaddingLeft + insets.left,
                    toolbarPaddingTop,
                    toolbarPaddingRight + insets.right,
                    toolbarPaddingBottom);
            scroll.setPadding(
                    scrollPaddingLeft + insets.left,
                    scrollPaddingTop,
                    scrollPaddingRight + insets.right,
                    scrollPaddingBottom + insets.bottom);
            grid.setPadding(
                    gridPaddingLeft + insets.left,
                    gridPaddingTop,
                    gridPaddingRight + insets.right,
                    gridPaddingBottom + insets.bottom);
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
