package com.starnest.common.ui.view.menuview;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\u0004\b\u0005\u0010\u0006J\u001a\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\u0006\u0010\u000e\u001a\u00020\u000fH\u0016J\u001a\u0010\u0010\u001a\u00020\u00112\b\u0010\u0012\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u0013\u001a\u00020\u000fH\u0016J\u0016\u0010\u0014\u001a\u00020\u00112\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u000fR\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\u0006\u00a8\u0006\u0018"}, d2 = {"Lcom/starnest/common/ui/view/menuview/MenuOptionAdapter;", "Lcom/starnest/core/ui/adapter/TMVVMAdapter;", "Lcom/starnest/common/ui/view/menuview/MenuOption;", "listener", "Lcom/starnest/common/ui/view/menuview/MenuOptionListener;", "<init>", "(Lcom/starnest/common/ui/view/menuview/MenuOptionListener;)V", "getListener", "()Lcom/starnest/common/ui/view/menuview/MenuOptionListener;", "setListener", "onCreateViewHolderBase", "Lcom/starnest/core/ui/adapter/TMVVMViewHolder;", "parent", "Landroid/view/ViewGroup;", "viewType", "", "onBindViewHolderBase", "", "holder", "position", "updateMenuTitle", "type", "Lcom/starnest/common/ui/view/menuview/MenuOptionType;", "newTitleResId", "common_debug"})
public final class MenuOptionAdapter extends com.starnest.core.ui.adapter.TMVVMAdapter<com.starnest.common.ui.view.menuview.MenuOption> {
    @org.jetbrains.annotations.NotNull()
    private com.starnest.common.ui.view.menuview.MenuOptionListener listener;
    
    public MenuOptionAdapter(@org.jetbrains.annotations.NotNull()
    com.starnest.common.ui.view.menuview.MenuOptionListener listener) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.starnest.common.ui.view.menuview.MenuOptionListener getListener() {
        return null;
    }
    
    public final void setListener(@org.jetbrains.annotations.NotNull()
    com.starnest.common.ui.view.menuview.MenuOptionListener p0) {
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
    
    public final void updateMenuTitle(@org.jetbrains.annotations.NotNull()
    com.starnest.common.ui.view.menuview.MenuOptionType type, int newTitleResId) {
    }
}