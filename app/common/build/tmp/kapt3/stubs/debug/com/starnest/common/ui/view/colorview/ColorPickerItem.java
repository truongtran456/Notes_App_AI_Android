package com.starnest.common.ui.view.colorview;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0007\bf\u0018\u00002\u00020\u0001R\u0018\u0010\u0002\u001a\u00020\u0003X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u0004\u0010\u0005\"\u0004\b\u0006\u0010\u0007R\u0012\u0010\b\u001a\u00020\tX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\nR\u0012\u0010\u000b\u001a\u00020\tX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\nR\u0012\u0010\f\u001a\u00020\tX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\nR\u0018\u0010\r\u001a\u00020\tX\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\r\u0010\n\"\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010\u00c0\u0006\u0003"}, d2 = {"Lcom/starnest/common/ui/view/colorview/ColorPickerItem;", "Lcom/starnest/core/data/model/Selectable;", "colorString", "", "getColorString", "()Ljava/lang/String;", "setColorString", "(Ljava/lang/String;)V", "isNone", "", "()Z", "isMore", "isAddColor", "isSelected", "setSelected", "(Z)V", "common_debug"})
public abstract interface ColorPickerItem extends com.starnest.core.data.model.Selectable {
    
    @org.jetbrains.annotations.NotNull()
    public abstract java.lang.String getColorString();
    
    public abstract void setColorString(@org.jetbrains.annotations.NotNull()
    java.lang.String p0);
    
    public abstract boolean isNone();
    
    public abstract boolean isMore();
    
    public abstract boolean isAddColor();
    
    @java.lang.Override()
    public abstract boolean isSelected();
    
    @java.lang.Override()
    public abstract void setSelected(boolean p0);
}