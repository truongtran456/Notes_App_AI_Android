package com.starnest.common.ui.view.headerview;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001:\u0001\u001cB\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0004\b\u0006\u0010\u0007J\b\u0010\b\u001a\u00020\tH\u0016J\b\u0010\u0010\u001a\u00020\u0011H\u0016J\b\u0010\u0019\u001a\u00020\u001aH\u0016J\b\u0010\u001b\u001a\u00020\u001aH\u0002R\u001c\u0010\n\u001a\u0004\u0018\u00010\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR(\u0010\u0014\u001a\u0004\u0018\u00010\u00132\b\u0010\u0012\u001a\u0004\u0018\u00010\u0013@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018\u00a8\u0006\u001d"}, d2 = {"Lcom/starnest/common/ui/view/headerview/HeaderView;", "Lcom/starnest/core/ui/widget/AbstractView;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "layoutId", "", "onHeaderViewClickListener", "Lcom/starnest/common/ui/view/headerview/HeaderView$OnHeaderViewClickListener;", "getOnHeaderViewClickListener", "()Lcom/starnest/common/ui/view/headerview/HeaderView$OnHeaderViewClickListener;", "setOnHeaderViewClickListener", "(Lcom/starnest/common/ui/view/headerview/HeaderView$OnHeaderViewClickListener;)V", "viewBinding", "Lcom/starnest/common/databinding/ItemHeaderViewBinding;", "value", "Lcom/starnest/common/ui/view/headerview/Header;", "header", "getHeader", "()Lcom/starnest/common/ui/view/headerview/Header;", "setHeader", "(Lcom/starnest/common/ui/view/headerview/Header;)V", "viewInitialized", "", "setupUI", "OnHeaderViewClickListener", "common_debug"})
public final class HeaderView extends com.starnest.core.ui.widget.AbstractView {
    @org.jetbrains.annotations.Nullable()
    private com.starnest.common.ui.view.headerview.HeaderView.OnHeaderViewClickListener onHeaderViewClickListener;
    @org.jetbrains.annotations.Nullable()
    private com.starnest.common.ui.view.headerview.Header header;
    
    public HeaderView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.util.AttributeSet attrs) {
        super(null, null);
    }
    
    @java.lang.Override()
    public int layoutId() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.starnest.common.ui.view.headerview.HeaderView.OnHeaderViewClickListener getOnHeaderViewClickListener() {
        return null;
    }
    
    public final void setOnHeaderViewClickListener(@org.jetbrains.annotations.Nullable()
    com.starnest.common.ui.view.headerview.HeaderView.OnHeaderViewClickListener p0) {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.starnest.common.databinding.ItemHeaderViewBinding viewBinding() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.starnest.common.ui.view.headerview.Header getHeader() {
        return null;
    }
    
    public final void setHeader(@org.jetbrains.annotations.Nullable()
    com.starnest.common.ui.view.headerview.Header value) {
    }
    
    @java.lang.Override()
    public void viewInitialized() {
    }
    
    private final void setupUI() {
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\b\u0010\u0002\u001a\u00020\u0003H&\u00a8\u0006\u0004\u00c0\u0006\u0003"}, d2 = {"Lcom/starnest/common/ui/view/headerview/HeaderView$OnHeaderViewClickListener;", "", "onSeeAll", "", "common_debug"})
    public static abstract interface OnHeaderViewClickListener {
        
        public abstract void onSeeAll();
    }
}