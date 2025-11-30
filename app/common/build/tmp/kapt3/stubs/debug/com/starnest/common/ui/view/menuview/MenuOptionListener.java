package com.starnest.common.ui.view.menuview;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0018\u0010\u0006\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\t\u00c0\u0006\u0003"}, d2 = {"Lcom/starnest/common/ui/view/menuview/MenuOptionListener;", "", "onClick", "", "menu", "Lcom/starnest/common/ui/view/menuview/MenuOption;", "onCheck", "isChecked", "", "common_debug"})
public abstract interface MenuOptionListener {
    
    public abstract void onClick(@org.jetbrains.annotations.NotNull()
    com.starnest.common.ui.view.menuview.MenuOption menu);
    
    public default void onCheck(@org.jetbrains.annotations.NotNull()
    com.starnest.common.ui.view.menuview.MenuOption menu, boolean isChecked) {
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        @java.lang.Deprecated()
        public static void onCheck(@org.jetbrains.annotations.NotNull()
        com.starnest.common.ui.view.menuview.MenuOptionListener $this, @org.jetbrains.annotations.NotNull()
        com.starnest.common.ui.view.menuview.MenuOption menu, boolean isChecked) {
        }
    }
}