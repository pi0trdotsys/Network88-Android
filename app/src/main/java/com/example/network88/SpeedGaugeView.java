package com.example.network88;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * The home screen's centrepiece: a circular speed gauge whose gradient arc fills
 * live during a test. Purely custom-drawn so it carries the app's identity rather
 * than looking like a stock widget.
 */
public class SpeedGaugeView extends View {

    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private float progress = 0f;            // 0..1 currently drawn
    private String value = "—";
    private String unit = "Mbps";
    private String label = "Ready";
    private int startColor = 0xFF4F8CFF;
    private int endColor = 0xFF33D69F;

    private ValueAnimator animator;

    public SpeedGaugeView(Context context) {
        super(context);
        init();
    }

    public SpeedGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(16 * d);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0xFF20293F);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(16 * d);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        capPaint.setStyle(Paint.Style.FILL);

        valuePaint.setColor(0xFFF2F5FB);
        valuePaint.setFakeBoldText(true);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(64 * d);

        unitPaint.setColor(0xFF9AA5BE);
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTextSize(18 * d);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(15 * d);
        labelPaint.setColor(0xFF9AA5BE);
        labelPaint.setLetterSpacing(0.12f);
    }

    /**
     * @param displayValue text shown in the centre (e.g. "250.4" or "—")
     * @param fraction     arc fill, 0..1
     * @param label        caption above the value
     * @param startColor   arc gradient start
     * @param endColor     arc gradient end
     */
    public void setData(String displayValue, float fraction, String label,
                        int startColor, int endColor) {
        this.value = displayValue;
        this.label = label;
        this.startColor = startColor;
        this.endColor = endColor;
        animateTo(Math.max(0f, Math.min(1f, fraction)));
        invalidate();
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(450);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float pad = 20 * d;
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - pad;
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        Shader shader = new SweepGradient(cx, cy,
                new int[]{startColor, endColor, endColor},
                new float[]{0f, SWEEP_ANGLE / 360f, 1f});
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRotate(START_ANGLE, cx, cy);
        shader.setLocalMatrix(m);
        progressPaint.setShader(shader);

        float sweep = SWEEP_ANGLE * progress;
        if (sweep > 0.5f) {
            canvas.drawArc(arcRect, START_ANGLE, sweep, false, progressPaint);
            // bright cap at the leading edge
            double a = Math.toRadians(START_ANGLE + sweep);
            float capX = cx + (float) (radius * Math.cos(a));
            float capY = cy + (float) (radius * Math.sin(a));
            capPaint.setColor(endColor);
            canvas.drawCircle(capX, capY, 9 * d, capPaint);
            capPaint.setColor(Color.WHITE);
            canvas.drawCircle(capX, capY, 3.5f * d, capPaint);
        }

        // centre text block
        float valueY = cy + valuePaint.getTextSize() / 3f;
        canvas.drawText(value, cx, valueY, valuePaint);
        canvas.drawText(unit, cx, valueY + 26 * d, unitPaint);
        canvas.drawText(label.toUpperCase(), cx, cy - radius / 2.1f, labelPaint);
    }
}
