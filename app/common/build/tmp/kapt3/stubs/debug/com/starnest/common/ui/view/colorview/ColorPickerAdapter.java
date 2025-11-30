package com.starnest.common.ui.view.colorview;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0004\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0002 !B\u0017\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0004\b\u0007\u0010\bJ\u001a\u0010\r\u001a\u00020\u000e2\b\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u001a\u0010\u0013\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u0016\u001a\u00020\u0012H\u0016J\u001a\u0010\u0017\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u0016\u001a\u00020\u0012H\u0002J\u001a\u0010\u0018\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u0016\u001a\u00020\u0012H\u0002J\u001a\u0010\u0019\u001a\u00020\u00142\b\u0010\u0015\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u0016\u001a\u00020\u0012H\u0002J\u0010\u0010\u001a\u001a\u00020\u00122\u0006\u0010\u0016\u001a\u00020\u0012H\u0016J&\u0010\u001b\u001a\u0004\u0018\u00010\u001c2\f\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00020\u001e2\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00020\u001eH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\""}, d2 = {"Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter;", "Lcom/starnest/core/ui/adapter/TMVVMAdapter;", "Lcom/starnest/common/ui/view/colorview/ColorPickerItem;", "context", "Landroid/content/Context;", "listener", "Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter$OnItemClickListener;", "<init>", "(Landroid/content/Context;Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter$OnItemClickListener;)V", "getContext", "()Landroid/content/Context;", "getListener", "()Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter$OnItemClickListener;", "onCreateViewHolderBase", "Lcom/starnest/core/ui/adapter/TMVVMViewHolder;", "parent", "Landroid/view/ViewGroup;", "viewType", "", "onBindViewHolderBase", "", "holder", "position", "configColorMoreLayout", "configColorNoneLayout", "configColorLayout", "getItemViewType", "getDiffCallback", "Landroidx/recyclerview/widget/DiffUtil$Callback;", "oldData", "", "newData", "OnItemClickListener", "ViewType", "common_debug"})
public final class ColorPickerAdapter extends com.starnest.core.ui.adapter.TMVVMAdapter<com.starnest.common.ui.view.colorview.ColorPickerItem> {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.starnest.common.ui.view.colorview.ColorPickerAdapter.OnItemClickListener listener = null;
    
    public ColorPickerAdapter(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.starnest.common.ui.view.colorview.ColorPickerAdapter.OnItemClickListener listener) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.content.Context getContext() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.starnest.common.ui.view.colorview.ColorPickerAdapter.OnItemClickListener getListener() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.starnest.core.ui.adapter.TMVVMViewHolder onCreateViewHolderBase(@org.jetbrains.annotations.Nullable()
    android.view.ViewGroup parent, int viewType) {
        return null;
    }
    
    @java.lang.Override()
    public void onBindViewHolderBase(@org.jetbrains.annotations.Nullable()
    com.starnest.core.ui.adapter.TMVVMViewHolder holder, int position) {
    }
    
    private final void configColorMoreLayout(com.starnest.core.ui.adapter.TMVVMViewHolder holder, int position) {
    }
    
    private final void configColorNoneLayout(com.starnest.core.ui.adapter.TMVVMViewHolder holder, int position) {
    }
    
    private final void configColorLayout(com.starnest.core.ui.adapter.TMVVMViewHolder holder, int position) {
    }
    
    @java.lang.Override()
    public int getItemViewType(int position) {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public androidx.recyclerview.widget.DiffUtil.Callback getDiffCallback(@org.jetbrains.annotations.NotNull()
    java.util.List<? extends com.starnest.common.ui.view.colorview.ColorPickerItem> oldData, @org.jetbrains.annotations.NotNull()
    java.util.List<? extends com.starnest.common.ui.view.colorview.ColorPickerItem> newData) {
        return null;
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&\u00a8\u0006\u0006\u00c0\u0006\u0003"}, d2 = {"Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter$OnItemClickListener;", "", "onItemClick", "", "color", "Lcom/starnest/common/ui/view/colorview/ColorPickerItem;", "common_debug"})
    public static abstract interface OnItemClickListener {
        
        public abstract void onItemClick(@org.jetbrains.annotations.NotNull()
        com.starnest.common.ui.view.colorview.ColorPickerItem color);
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"Lcom/starnest/common/ui/view/colorview/ColorPickerAdapter$ViewType;", "", "<init>", "()V", "COLOR", "", "COLOR_MORE", "COLOR_NONE", "common_debug"})
    public static final class ViewType {
        public static final int COLOR = 0;
        public static final int COLOR_MORE = 1;
        public static final int COLOR_NONE = 2;
        @org.jetbrains.annotations.NotNull()
        public static final com.starnest.common.ui.view.colorview.ColorPickerAdapter.ViewType INSTANCE = null;
        
        private ViewType() {
            super();
        }
    }
}