package com.starnest.common.ui.view.menuview;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 \u001d2\u00020\u0001:\u0001\u001dB\u0019\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0004\b\u0006\u0010\u0007J\b\u0010\u0016\u001a\u00020\u0017H\u0016J\b\u0010\u0018\u001a\u00020\u0019H\u0016J\b\u0010\u001a\u001a\u00020\u001bH\u0016J\b\u0010\u001c\u001a\u00020\u001bH\u0002R*\u0010\b\u001a\u0012\u0012\u0004\u0012\u00020\n0\tj\b\u0012\u0004\u0012\u00020\n`\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001c\u0010\u0010\u001a\u0004\u0018\u00010\u0011X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u0013\"\u0004\b\u0014\u0010\u0015\u00a8\u0006\u001e"}, d2 = {"Lcom/starnest/common/ui/view/menuview/MenuView;", "Lcom/starnest/core/ui/widget/AbstractView;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "chatMenus", "Ljava/util/ArrayList;", "Lcom/starnest/common/ui/view/menuview/MenuOption;", "Lkotlin/collections/ArrayList;", "getChatMenus", "()Ljava/util/ArrayList;", "setChatMenus", "(Ljava/util/ArrayList;)V", "listener", "Lcom/starnest/common/ui/view/menuview/MenuOptionListener;", "getListener", "()Lcom/starnest/common/ui/view/menuview/MenuOptionListener;", "setListener", "(Lcom/starnest/common/ui/view/menuview/MenuOptionListener;)V", "layoutId", "", "viewBinding", "Lcom/starnest/common/databinding/ItemMenuViewBinding;", "viewInitialized", "", "setupRecyclerView", "Companion", "common_debug"})
public final class MenuView extends com.starnest.core.ui.widget.AbstractView {
    @org.jetbrains.annotations.NotNull()
    private java.util.ArrayList<com.starnest.common.ui.view.menuview.MenuOption> chatMenus;
    @org.jetbrains.annotations.Nullable()
    private com.starnest.common.ui.view.menuview.MenuOptionListener listener;
    @org.jetbrains.annotations.NotNull()
    public static final com.starnest.common.ui.view.menuview.MenuView.Companion Companion = null;
    
    public MenuView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs) {
        super(null, null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.ArrayList<com.starnest.common.ui.view.menuview.MenuOption> getChatMenus() {
        return null;
    }
    
    public final void setChatMenus(@org.jetbrains.annotations.NotNull()
    java.util.ArrayList<com.starnest.common.ui.view.menuview.MenuOption> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.starnest.common.ui.view.menuview.MenuOptionListener getListener() {
        return null;
    }
    
    public final void setListener(@org.jetbrains.annotations.Nullable()
    com.starnest.common.ui.view.menuview.MenuOptionListener p0) {
    }
    
    @java.lang.Override()
    public int layoutId() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.starnest.common.databinding.ItemMenuViewBinding viewBinding() {
        return null;
    }
    
    @java.lang.Override()
    public void viewInitialized() {
    }
    
    private final void setupRecyclerView() {
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003JG\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0016\u0010\n\u001a\u0012\u0012\u0004\u0012\u00020\f0\u000bj\b\u0012\u0004\u0012\u00020\f`\r2\u0006\u0010\u000e\u001a\u00020\u000f2\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u0011\u00a2\u0006\u0002\u0010\u0012J \u0010\u0013\u001a\u00020\u00052\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u0016\u001a\u00020\tH\u0002\u00a8\u0006\u0017"}, d2 = {"Lcom/starnest/common/ui/view/menuview/MenuView$Companion;", "", "<init>", "()V", "show", "", "context", "Landroid/content/Context;", "anchor", "Landroid/view/View;", "menus", "Ljava/util/ArrayList;", "Lcom/starnest/common/ui/view/menuview/MenuOption;", "Lkotlin/collections/ArrayList;", "listener", "Lcom/starnest/common/ui/view/menuview/MenuOptionListener;", "width", "", "(Landroid/content/Context;Landroid/view/View;Ljava/util/ArrayList;Lcom/starnest/common/ui/view/menuview/MenuOptionListener;Ljava/lang/Integer;)V", "showPopupAtLocation", "popupWindow", "Landroid/widget/PopupWindow;", "contentView", "common_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void show(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        android.view.View anchor, @org.jetbrains.annotations.NotNull()
        java.util.ArrayList<com.starnest.common.ui.view.menuview.MenuOption> menus, @org.jetbrains.annotations.NotNull()
        com.starnest.common.ui.view.menuview.MenuOptionListener listener, @org.jetbrains.annotations.Nullable()
        java.lang.Integer width) {
        }
        
        private final void showPopupAtLocation(android.widget.PopupWindow popupWindow, android.view.View anchor, android.view.View contentView) {
        }
    }
}