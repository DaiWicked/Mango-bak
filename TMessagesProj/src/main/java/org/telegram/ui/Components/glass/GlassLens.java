package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

/**
 * A single floating glass lens that refracts whatever is drawn underneath it.
 * <p>
 * The owner view is expected to do the recording, because the lens has to capture a
 * snapshot of the view's own content:
 * <pre>
 *     final RecordingCanvas c = lens.beginRecord(w, h, getBackground());
 *     if (c != null) {
 *         super.dispatchDraw(c);
 *         lens.endRecord();
 *     }
 *     lens.draw(canvas);
 *     super.dispatchDraw(canvas);
 * </pre>
 */
public class GlassLens {
    private final @Nullable BlurredBackgroundSourceRenderNode lensBackgroundSourceNode;
    private final @NonNull BlurredBackgroundSourceColor lensBackgroundSourceColor;
    public final BlurredBackgroundDrawable lensDrawable;

    private int lensColorForeground;
    private float lensVisibility;

    private final Rect lensBounds = new Rect();
    private final Rect lensBoundsForeground = new Rect();
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassLens() {
        lensBackgroundSourceColor = new BlurredBackgroundSourceColor();

        final BlurredBackgroundDrawableViewFactory lensBackgroundFactory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lensBackgroundSourceNode = new BlurredBackgroundSourceRenderNode(lensBackgroundSourceColor);
            lensBackgroundSourceNode.setBlur(dp(4f));
            lensBackgroundFactory = new BlurredBackgroundDrawableViewFactory(lensBackgroundSourceNode);
            lensBackgroundFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        } else {
            lensBackgroundSourceNode = null;
            lensBackgroundFactory = new BlurredBackgroundDrawableViewFactory(lensBackgroundSourceColor);
        }

        lensDrawable = lensBackgroundFactory.create();
        lensDrawable.setColorProvider(new BlurredBackgroundColorProviderThemed(null, Theme.key_windowBackgroundWhite) {
            @Override
            public int getBackgroundColor() {
                return 0;
            }

            @Override
            public int getShadowColor() {
                return 0;
            }
        });
    }

    public void setColor(int lensBackgroundColor, int lensForegroundColor) {
        this.lensColorForeground = lensForegroundColor;
        lensPaint.setColor(lensBackgroundColor);
    }

    public void setBounds(int l, int t, int r, int b) {
        lensBounds.set(l, t, r, b);
        checkBounds();
    }

    public void setVisibility(float visibility) {
        lensVisibility = visibility;
        lensDrawable.setAlpha(MathUtils.clamp((int) (255 * visibility), 0, 255));
        checkBounds();
    }

    private void checkBounds() {
        final int i = dp(7f * lensVisibility);
        lensBoundsForeground.set(lensBounds);
        lensBoundsForeground.inset(-i, -i);

        lensDrawable.setBounds(lensBoundsForeground);
        lensDrawable.setRadius(Math.min(lensBoundsForeground.width(), lensBoundsForeground.height()) / 2f);
    }

    /**
     * Starts capturing the owner's content. Returns null when the platform cannot record,
     * in which case the owner must skip the capture and only draw the lens.
     */
    public @Nullable RecordingCanvas beginRecord(int width, int height, @Nullable Drawable background) {
        if (lensBackgroundSourceNode == null || lensBackgroundSourceNode.inRecording()) {
            return null;
        }

        final RecordingCanvas c = lensBackgroundSourceNode.beginRecording(width, height);
        if (background != null) {
            background.draw(c);
        }

        c.drawColor(lensColorForeground);
        return c;
    }

    public void endRecord() {
        if (lensBackgroundSourceNode != null && lensBackgroundSourceNode.inRecording()) {
            lensBackgroundSourceNode.endRecording();
        }
    }

    public void draw(Canvas canvas) {
        if (lensDrawable.getAlpha() != 255) {
            final float r = Math.min(lensBounds.width() / 2f, lensBounds.height() / 2f);
            canvas.drawRoundRect(lensBounds.left, lensBounds.top,
                    lensBounds.right, lensBounds.bottom, r, r, lensPaint);
        }

        if (lensDrawable.getAlpha() != 0) {
            lensDrawable.draw(canvas);
        }
    }
}
