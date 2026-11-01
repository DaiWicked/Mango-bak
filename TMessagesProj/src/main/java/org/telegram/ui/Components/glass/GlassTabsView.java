package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.LayoutHelper;

public class GlassTabsView extends FrameLayout {
    public final GlassLens glassLens = new GlassLens();

    public final LinearLayout linearLayout;

    public GlassTabsView(@NonNull Context context) {
        super(context);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    protected void setLensColor(int lensBackgroundColor, int lensForegroundColor) {
        glassLens.setColor(lensBackgroundColor, lensForegroundColor);
    }

    protected void setLensBounds(int l, int t, int r, int b) {
        glassLens.setBounds(l, t, r, b);
    }

    protected void setLensVisibility(float visibility) {
        glassLens.setVisibility(visibility);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final RecordingCanvas c = glassLens.beginRecord(getMeasuredWidth(), getMeasuredHeight(), getBackground());
        if (c != null) {
            super.dispatchDraw(c);
            glassLens.endRecord();
        }

        glassLens.draw(canvas);
        super.dispatchDraw(canvas);
    }
}
