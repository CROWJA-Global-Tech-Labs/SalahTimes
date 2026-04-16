package com.crowja.sholatku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Compass view that rotates its dial with the device heading and
 * draws a green triangular pointer at the Qibla bearing.
 */
public class CompassView extends View {
    private float azimuth;
    private float qiblaBearing;

    private final Paint dialPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint qiblaPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint qiblaEdge   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDot   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint northPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path triangle = new Path();
    private final RectF dialRect = new RectF();

    public CompassView(Context c) { super(c); init(); }
    public CompassView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }

    private void init() {
        dialPaint.setStyle(Paint.Style.STROKE);
        dialPaint.setColor(Color.parseColor("#064E3B"));
        dialPaint.setStrokeWidth(dp(3));

        tickPaint.setColor(Color.parseColor("#0F172A"));
        tickPaint.setStrokeWidth(dp(1.5f));

        textPaint.setColor(Color.parseColor("#0F172A"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        northPaint.setColor(Color.parseColor("#DC2626"));
        northPaint.setTextAlign(Paint.Align.CENTER);
        northPaint.setFakeBoldText(true);

        qiblaPaint.setColor(Color.parseColor("#10B981"));
        qiblaPaint.setStyle(Paint.Style.FILL);
        qiblaEdge.setColor(Color.parseColor("#064E3B"));
        qiblaEdge.setStyle(Paint.Style.STROKE);
        qiblaEdge.setStrokeWidth(dp(2));

        centerDot.setColor(Color.parseColor("#064E3B"));
        centerDot.setStyle(Paint.Style.FILL);

        pointerPaint.setColor(Color.parseColor("#0F172A"));
        pointerPaint.setStyle(Paint.Style.FILL);
    }

    public void setAzimuth(float az)   { this.azimuth = az; invalidate(); }
    public void setQibla(float bearing){ this.qiblaBearing = bearing; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) / 2f - dp(16);

        textPaint.setTextSize(dp(16));
        northPaint.setTextSize(dp(20));

        dialRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawCircle(cx, cy, radius, dialPaint);

        canvas.save();
        canvas.rotate(-azimuth, cx, cy);

        for (int deg = 0; deg < 360; deg += 15) {
            boolean cardinal = deg % 90 == 0;
            float len = cardinal ? dp(14) : dp(8);
            float rad = (float) Math.toRadians(deg - 90);
            float sx = cx + (radius) * (float) Math.cos(rad);
            float sy = cy + (radius) * (float) Math.sin(rad);
            float ex = cx + (radius - len) * (float) Math.cos(rad);
            float ey = cy + (radius - len) * (float) Math.sin(rad);
            tickPaint.setStrokeWidth(cardinal ? dp(2.5f) : dp(1f));
            canvas.drawLine(sx, sy, ex, ey, tickPaint);
        }

        drawCardinal(canvas, cx, cy, radius - dp(32),   0, "N", northPaint);
        drawCardinal(canvas, cx, cy, radius - dp(32),  90, "E", textPaint);
        drawCardinal(canvas, cx, cy, radius - dp(32), 180, "S", textPaint);
        drawCardinal(canvas, cx, cy, radius - dp(32), 270, "W", textPaint);

        float qRad = (float) Math.toRadians(qiblaBearing - 90);
        float tipX = cx + (radius - dp(4)) * (float) Math.cos(qRad);
        float tipY = cy + (radius - dp(4)) * (float) Math.sin(qRad);
        float baseX = cx + (radius - dp(38)) * (float) Math.cos(qRad);
        float baseY = cy + (radius - dp(38)) * (float) Math.sin(qRad);
        float perpX = -(float) Math.sin(qRad);
        float perpY =  (float) Math.cos(qRad);
        float halfBase = dp(14);
        triangle.reset();
        triangle.moveTo(tipX, tipY);
        triangle.lineTo(baseX + perpX * halfBase, baseY + perpY * halfBase);
        triangle.lineTo(baseX - perpX * halfBase, baseY - perpY * halfBase);
        triangle.close();
        canvas.drawPath(triangle, qiblaPaint);
        canvas.drawPath(triangle, qiblaEdge);

        canvas.restore();

        // Fixed top-center pointer marks the direction the device is facing.
        triangle.reset();
        triangle.moveTo(cx, cy - radius - dp(4));
        triangle.lineTo(cx - dp(10), cy - radius + dp(14));
        triangle.lineTo(cx + dp(10), cy - radius + dp(14));
        triangle.close();
        canvas.drawPath(triangle, pointerPaint);

        canvas.drawCircle(cx, cy, dp(6), centerDot);
    }

    private void drawCardinal(Canvas canvas, float cx, float cy, float r,
                               int deg, String label, Paint paint) {
        float rad = (float) Math.toRadians(deg - 90);
        float x = cx + r * (float) Math.cos(rad);
        float y = cy + r * (float) Math.sin(rad) + paint.getTextSize() / 3f;
        canvas.drawText(label, x, y, paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
