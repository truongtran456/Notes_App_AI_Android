package com.starnest.common.ui.view.colorview;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\u0004\u0018\u00002\u00020\u0001:\u0001$B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0004\b\u0006\u0010\u0007J\b\u0010\b\u001a\u00020\tH\u0016J\b\u0010\u0016\u001a\u00020\u0017H\u0016J\b\u0010 \u001a\u00020!H\u0016J\b\u0010\"\u001a\u00020!H\u0002J\b\u0010#\u001a\u00020!H\u0002R\u001c\u0010\n\u001a\u0004\u0018\u00010\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001b\u0010\u0010\u001a\u00020\u00118BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0014\u0010\u0015\u001a\u0004\b\u0012\u0010\u0013R0\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u001a0\u0019@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001c\u0010\u001d\"\u0004\b\u001e\u0010\u001f\u00a8\u0006%"}, d2 = {"Lcom/starnest/common/ui/view/colorview/ColorPickerView;", "Lcom/starnest/core/ui/widget/AbstractView;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "layoutId", "", "listener", "Lcom/starnest/common/ui/view/colorview/ColorPickerView$OnItemClickListener;", "getListener", "()Lcom/starnest/common/ui/view/colorview/ColorPickerView$OnItemClickListener;", "setListener", "(Lcom/starnest/common/ui/view/colorview/ColorPickerView$OnItemClickListener;)V", "colorAdapter", "Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter;", "getColorAdapter", "()Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter;", "colorAdapter$delegate", "Lkotlin/Lazy;", "viewBinding", "Lcom/starnest/common/databinding/ItemColorViewBinding;", "value", "", "Lcom/starnest/common/ui/view/colorview/ColorPickerItem;", "colors", "getColors", "()Ljava/util/List;", "setColors", "(Ljava/util/List;)V", "viewInitialized", "", "setupRecyclerView", "setupLayoutManager", "OnItemClickListener", "common_debug"})
public final class ColorPickerView extends com.starnest.core.ui.widget.AbstractView {
    @org.jetbrains.annotations.Nullable()
    private com.starnest.common.ui.view.colorview.ColorPickerView.OnItemClickListener listener;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy colorAdapter$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<? extends com.starnest.common.ui.view.colorview.ColorPickerItem> colors;
    
    public ColorPickerView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.util.AttributeSet attrs) {
        super(null, null);
    }
    
    @java.lang.Override()
    public int layoutId() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.starnest.common.ui.view.colorview.ColorPickerView.OnItemClickListener getListener() {
        return null;
    }
    
    public final void setListener(@org.jetbrains.annotations.Nullable()
    com.starnest.common.ui.view.colorview.ColorPickerView.OnItemClickListener p0) {
    }
    
    private final com.starnest.common.ui.view.colorview.ColorPickerAdapter getColorAdapter() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.starnest.common.databinding.ItemColorViewBinding viewBinding() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.starnest.common.ui.view.colorview.ColorPickerItem> getColors() {
        return null;
    }
    
    public final void setColors(@org.jetbrains.annotations.NotNull()
    java.util.List<? extends com.starnest.common.ui.view.colorview.ColorPickerItem> value) {
    }
    
    @java.lang.Override()
    public void viewInitialized() {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void setupLayoutManager() {
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&\u00a8\u0006\u0006\u00c0\u0006\u0003"}, d2 = {"Lcom/starnest/common/ui/view/colorview/ColorPickerView$OnItemClickListener;", "", "onItemClick", "", "color", "Lcom/starnest/common/ui/view/colorview/ColorPickerItem;", "common_debug"})
    public static abstract interface OnItemClickListener {
        
        public abstract void onItemClick(@org.jetbrains.annotations.NotNull()
        com.starnest.common.ui.view.colorview.ColorPickerItem color);
    }
}