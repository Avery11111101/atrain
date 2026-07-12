package com.avery.atrain.gui;

import org.bukkit.inventory.Inventory;

/** 統一 GUI 底部操作列槽位，避免返回鍵覆蓋建立按鈕 */
public final class GuiSlots {
  public static final int CREATE = 49;
  public static final int PREV_PAGE = 45;
  public static final int NEXT_PAGE = 53;

  private GuiSlots() {}

  public static int back(Inventory inv) {
    return inv.getSize() >= 54 ? 48 : inv.getSize() - 5;
  }
}
