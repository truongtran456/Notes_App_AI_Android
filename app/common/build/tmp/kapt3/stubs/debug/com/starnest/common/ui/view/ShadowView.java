package com.starnest.common.ui.view;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\r\b\u0016\u0018\u00002\u00020\u0001B\'\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0004\b\b\u0010\tJ\u0010\u0010!\u001a\u00020\"2\u0006\u0010\u0004\u001a\u00020\u0005H\u0002J\b\u0010#\u001a\u00020\"H\u0002J(\u0010$\u001a\u00020\"2\u0006\u0010%\u001a\u00020\u00072\u0006\u0010&\u001a\u00020\u00072\u0006\u0010\'\u001a\u00020\u00072\u0006\u0010(\u001a\u00020\u0007H\u0014J(\u0010)\u001a\u00020\"2\u0006\u0010\f\u001a\u00020\u00072\u0006\u0010\r\u001a\u00020\u00072\b\u0010\u0016\u001a\u0004\u0018\u00010\u00172\u0006\u0010*\u001a\u00020\u0007J\u0010\u0010+\u001a\u00020\"2\u0006\u0010,\u001a\u00020\u001bH\u0014J\b\u0010-\u001a\u00020\"H\u0014J\b\u0010.\u001a\u00020\"H\u0014R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R$\u0010\u0011\u001a\u00020\u00072\u0006\u0010\u0010\u001a\u00020\u0007@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u0013\"\u0004\b\u0014\u0010\u0015R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0018\u001a\u0004\u0018\u00010\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u001bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R$\u0010\u001d\u001a\u00020\u001c2\u0006\u0010\u0010\u001a\u00020\u001c@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u001e\"\u0004\b\u001f\u0010 \u00a8\u0006/"}, d2 = {"Lcom/starnest/common/ui/view/ShadowView;", "Landroid/widget/FrameLayout;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "defStyle", "", "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;I)V", "shadowPaint", "Landroid/graphics/Paint;", "dx", "dy", "savedDx", "savedDy", "value", "shadowColor", "getShadowColor", "()I", "setShadowColor", "(I)V", "shadowMask", "Landroid/graphics/drawable/Drawable;", "shadowBitmap", "Landroid/graphics/Bitmap;", "shadowCanvas", "Landroid/graphics/Canvas;", "", "isShadowEnabled", "()Z", "setShadowEnabled", "(Z)V", "initAttr", "", "updatePadding", "onSizeChanged", "w", "h", "oldw", "oldh", "configShadow", "color", "onDraw", "canvas", "onAttachedToWindow", "onDetachedFromWindow", "common_debug"})
public class ShadowView extends android.widget.FrameLayout {
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint shadowPaint = null;
    private int dx = 0;
    private int dy = 0;
    private int savedDx = 0;
    private int savedDy = 0;
    private int shadowColor;
    @org.jetbrains.annotations.Nullable()
    private android.graphics.drawable.Drawable shadowMask;
    @org.jetbrains.annotations.Nullable()
    private android.graphics.Bitmap shadowBitmap;
    @org.jetbrains.annotations.Nullable()
    private android.graphics.Canvas shadowCanvas;
    private boolean isShadowEnabled = true;
    
    @kotlin.jvm.JvmOverloads()
    public ShadowView(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super(null);
    }
    
    @kotlin.jvm.JvmOverloads()
    public ShadowView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs) {
        super(null);
    }
    
    @kotlin.jvm.JvmOverloads()
    public ShadowView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs, int defStyle) {
        super(null);
    }
    
    public final int getShadowColor() {
        return 0;
    }
    
    public final void setShadowColor(int value) {
    }
    
    public final boolean isShadowEnabled() {
        return false;
    }
    
    public final void setShadowEnabled(boolean value) {
    }
    
    private final void initAttr(android.util.AttributeSet attrs) {
    }
    
    private final void updatePadding() {
    }
    
    @java.lang.Override()
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    }
    
    public final void configShadow(int dx, int dy, @org.jetbrains.annotations.Nullable()
    android.graphics.drawable.Drawable shadowMask, int color) {
    }
    
    @java.lang.Override()
    protected void onDraw(@org.jetbrains.annotations.NotNull()
    android.graphics.Canvas canvas) {
    }
    
    @java.lang.Override()
    protected void onAttachedToWindow() {
    }
    
    @java.lang.Override()
    protected void onDetachedFromWindow() {
    }
}