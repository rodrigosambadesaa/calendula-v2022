package es.usc.citius.servando.calendula.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * RelativeLayout that preserves its XML padding and adds the current system-bar/cutout insets.
 */
public class SystemBarPaddingRelativeLayout extends RelativeLayout {

    private final int basePaddingLeft;
    private final int basePaddingTop;
    private final int basePaddingRight;
    private final int basePaddingBottom;

    public SystemBarPaddingRelativeLayout(@NonNull Context context) {
        this(context, null);
    }

    public SystemBarPaddingRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SystemBarPaddingRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        basePaddingLeft = getPaddingLeft();
        basePaddingTop = getPaddingTop();
        basePaddingRight = getPaddingRight();
        basePaddingBottom = getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(this, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    basePaddingLeft + insets.left,
                    basePaddingTop + insets.top,
                    basePaddingRight + insets.right,
                    basePaddingBottom + insets.bottom);
            return windowInsets;
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewCompat.requestApplyInsets(this);
    }
}
