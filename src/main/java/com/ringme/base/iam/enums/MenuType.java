package com.ringme.base.iam.enums;

/**
 * Loại node trong cây Menu (độc lập với Permission — chỉ phục vụ điều hướng UI).
 * DIRECTORY: thư mục chứa menu con, không có route riêng.
 * MENU: có route thật (path + component phía Angular).
 * BUTTON: hành động UI mịn hơn menu (vd nút trong 1 trang), không có route riêng.
 */
public enum MenuType {
    DIRECTORY,
    MENU,
    BUTTON,
}
