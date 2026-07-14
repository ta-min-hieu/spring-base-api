-- Module phân quyền RBAC: Role, Permission (API resource: HTTP method + URL pattern), Menu (cây,
-- ĐỘC LẬP với Permission), UserRole, RolePermission, RoleMenu.
-- Không có Flyway/Liquibase trong repo này (xem CLAUDE.md) nên script này chỉ để tham khảo/tái tạo thủ công;
-- chạy trực tiếp vào container oracle-xe (schema dev_e_commerce) qua sqlplus, giống sql/oracle/upload_file.sql.
--
-- Thiết kế: role.data_scope là cột để dành cho việc mở rộng Data Permission trong tương lai
-- (ALL/SELF/DEPT/CUSTOM) - hiện tại CHƯA có logic lọc dữ liệu theo scope, chỉ lưu placeholder.
-- Quyền API (Permission) tách biệt hoàn toàn khỏi Menu: Menu chỉ phục vụ hiển thị điều hướng,
-- Permission chỉ phục vụ kiểm soát API (method + URL pattern kiểu Ant, khớp bằng AntPathMatcher).

CREATE TABLE dev_e_commerce.role (
    id          NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    role_key    VARCHAR2(50)  NOT NULL,
    role_name   VARCHAR2(100) NOT NULL,
    description VARCHAR2(255),
    data_scope  VARCHAR2(20)  DEFAULT 'ALL' NOT NULL,
    status      VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL,
    sort_order  NUMBER(10)    DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_role_key UNIQUE (role_key)
);

-- code = định danh ổn định dùng với hasAuthority('PERM_' || code) trong @PreAuthorize.
-- http_method = GET/POST/PUT/PATCH/DELETE hoặc '*' (khớp mọi method). url_pattern kiểu Ant (/v1/x/**, /v1/x/*).
CREATE TABLE dev_e_commerce.permission (
    id           NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    code         VARCHAR2(100) NOT NULL,
    name         VARCHAR2(150) NOT NULL,
    http_method  VARCHAR2(10)  NOT NULL,
    url_pattern  VARCHAR2(255) NOT NULL,
    description  VARCHAR2(255),
    status       VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL,
    created_at   TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_permission_code UNIQUE (code),
    CONSTRAINT uq_permission_resource UNIQUE (http_method, url_pattern)
);

-- Cây menu ĐỘC LẬP với Permission — chỉ phục vụ điều hướng UI. parent_id NULL = node gốc.
-- menu_type: DIRECTORY (thư mục chứa menu con, không có route) / MENU (có route, path+component) /
-- BUTTON (hành động UI mịn hơn menu, vd nút "Xoá" trong 1 trang - không tự có route riêng).
CREATE TABLE dev_e_commerce.menu (
    id          NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    parent_id   NUMBER,
    name        VARCHAR2(100) NOT NULL,
    path        VARCHAR2(255),
    component   VARCHAR2(255),
    icon        VARCHAR2(100),
    menu_type   VARCHAR2(20)  NOT NULL,
    sort_order  NUMBER(10)    DEFAULT 0 NOT NULL,
    visible     NUMBER(1)     DEFAULT 1 NOT NULL,
    status      VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL,
    created_at  TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id)
        REFERENCES dev_e_commerce.menu (id) ON DELETE CASCADE
);

CREATE INDEX idx_menu_parent ON dev_e_commerce.menu (parent_id);

-- user_id trỏ tới app_user.id nhưng KHÔNG map quan hệ JPA hai chiều với AppUser (giữ AppUser đơn
-- giản, không phụ thuộc ngược vào module RBAC) — chỉ ràng buộc FK ở tầng DB.
CREATE TABLE dev_e_commerce.user_role (
    id         NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    user_id    NUMBER NOT NULL,
    role_id    NUMBER NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES dev_e_commerce.app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES dev_e_commerce.role (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_role_user ON dev_e_commerce.user_role (user_id);
CREATE INDEX idx_user_role_role ON dev_e_commerce.user_role (role_id);

CREATE TABLE dev_e_commerce.role_permission (
    id            NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    role_id       NUMBER NOT NULL,
    permission_id NUMBER NOT NULL,
    created_at    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES dev_e_commerce.role (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES dev_e_commerce.permission (id) ON DELETE CASCADE,
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permission_role ON dev_e_commerce.role_permission (role_id);

CREATE TABLE dev_e_commerce.role_menu (
    id         NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    role_id    NUMBER NOT NULL,
    menu_id    NUMBER NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id)
        REFERENCES dev_e_commerce.role (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id)
        REFERENCES dev_e_commerce.menu (id) ON DELETE CASCADE,
    CONSTRAINT uq_role_menu UNIQUE (role_id, menu_id)
);

CREATE INDEX idx_role_menu_role ON dev_e_commerce.role_menu (role_id);


-- ============================== SEED DATA ==============================
-- role_key phải khớp giá trị dùng trong claim "roles" của JWT (không có tiền tố ROLE_ — tiền tố
-- được JwtAuthenticationServiceImpl/JwtAuthenticationFilter tự thêm khi ánh xạ thành GrantedAuthority).

INSERT INTO dev_e_commerce.role (role_key, role_name, description, data_scope, sort_order) VALUES
    ('ADMIN', 'Quản trị viên', 'Toàn quyền quản trị hệ thống', 'ALL', 1);
INSERT INTO dev_e_commerce.role (role_key, role_name, description, data_scope, sort_order) VALUES
    ('USER', 'Người dùng', 'Người dùng thông thường, chỉ đọc dữ liệu công khai', 'SELF', 2);

-- Permission: khớp đúng các endpoint hiện có (xem controller/v1/ProductController, FileController).
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('product:list', 'Xem danh sách sản phẩm', 'GET', '/v1/products', 'Liệt kê sản phẩm (phân trang)');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('product:detail', 'Xem chi tiết sản phẩm', 'GET', '/v1/products/*', 'Xem 1 sản phẩm theo id');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('product:create', 'Tạo sản phẩm', 'POST', '/v1/products', 'Tạo mới sản phẩm (kèm upload file)');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('product:update', 'Cập nhật sản phẩm', 'PUT', '/v1/products/*', 'Sửa sản phẩm theo id');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('product:delete', 'Xoá sản phẩm', 'DELETE', '/v1/products/*', 'Xoá sản phẩm theo id');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('product:delete-file', 'Gỡ file khỏi sản phẩm', 'DELETE', '/v1/products/*/files/*', 'Gỡ 1 file khỏi gallery sản phẩm');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('file:download', 'Tải file', 'GET', '/v1/files/*', 'Tải/xem 1 file đã upload theo id');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('file:upload-init', 'Khởi tạo phiên upload', 'POST', '/v1/files/uploads', 'Bắt đầu 1 phiên upload theo chunk');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('file:upload-chunk', 'Upload 1 chunk', 'PUT', '/v1/files/uploads/*/chunks/*', 'Ghi 1 đoạn (chunk) file');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('file:upload-complete', 'Hoàn tất upload', 'POST', '/v1/files/uploads/*/complete', 'Ghép các chunk thành file hoàn chỉnh');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('file:upload-delete', 'Huỷ phiên upload', 'DELETE', '/v1/files/uploads/*', 'Huỷ 1 phiên upload đang dang dở');
INSERT INTO dev_e_commerce.permission (code, name, http_method, url_pattern, description) VALUES
    ('rbac:manage', 'Quản trị RBAC', '*', '/v1/rbac/**', 'Toàn quyền quản lý user/role/permission/menu');

-- ADMIN: toàn bộ permission hiện có.
INSERT INTO dev_e_commerce.role_permission (role_id, permission_id)
    SELECT r.id, p.id FROM dev_e_commerce.role r CROSS JOIN dev_e_commerce.permission p
    WHERE r.role_key = 'ADMIN';

-- USER: chỉ đọc (list/detail sản phẩm + tải file).
INSERT INTO dev_e_commerce.role_permission (role_id, permission_id)
    SELECT r.id, p.id FROM dev_e_commerce.role r CROSS JOIN dev_e_commerce.permission p
    WHERE r.role_key = 'USER' AND p.code IN ('product:list', 'product:detail', 'file:download');

-- Menu mẫu: nhóm "Hệ thống" (quản trị RBAC) + "Sản phẩm".
INSERT INTO dev_e_commerce.menu (parent_id, name, path, component, icon, menu_type, sort_order) VALUES
    (NULL, 'Hệ thống', '/system', NULL, 'settings', 'DIRECTORY', 1);
INSERT INTO dev_e_commerce.menu (parent_id, name, path, component, icon, menu_type, sort_order)
    SELECT id, 'Người dùng', '/system/user', 'system/user/user-list', 'person', 'MENU', 1
    FROM dev_e_commerce.menu WHERE name = 'Hệ thống' AND parent_id IS NULL;
INSERT INTO dev_e_commerce.menu (parent_id, name, path, component, icon, menu_type, sort_order)
    SELECT id, 'Vai trò', '/system/role', 'system/role/role-list', 'badge', 'MENU', 2
    FROM dev_e_commerce.menu WHERE name = 'Hệ thống' AND parent_id IS NULL;
INSERT INTO dev_e_commerce.menu (parent_id, name, path, component, icon, menu_type, sort_order)
    SELECT id, 'Menu', '/system/menu', 'system/menu/menu-list', 'menu', 'MENU', 3
    FROM dev_e_commerce.menu WHERE name = 'Hệ thống' AND parent_id IS NULL;
INSERT INTO dev_e_commerce.menu (parent_id, name, path, component, icon, menu_type, sort_order)
    SELECT id, 'Quyền', '/system/permission', 'system/permission/permission-list', 'lock', 'MENU', 4
    FROM dev_e_commerce.menu WHERE name = 'Hệ thống' AND parent_id IS NULL;
INSERT INTO dev_e_commerce.menu (parent_id, name, path, component, icon, menu_type, sort_order) VALUES
    (NULL, 'Sản phẩm', '/products', 'products/product-list', 'inventory', 'MENU', 2);

-- ADMIN thấy toàn bộ menu; USER chỉ thấy "Sản phẩm".
INSERT INTO dev_e_commerce.role_menu (role_id, menu_id)
    SELECT r.id, m.id FROM dev_e_commerce.role r CROSS JOIN dev_e_commerce.menu m
    WHERE r.role_key = 'ADMIN';
INSERT INTO dev_e_commerce.role_menu (role_id, menu_id)
    SELECT r.id, m.id FROM dev_e_commerce.role r CROSS JOIN dev_e_commerce.menu m
    WHERE r.role_key = 'USER' AND m.name = 'Sản phẩm';

-- Gán role cho user hiện có (xem SELECT id, username FROM app_user để đối chiếu id).
-- testuser (id=1) làm ADMIN; qa_frontend (id=21) và qa_guest (id=41) làm USER.
INSERT INTO dev_e_commerce.user_role (user_id, role_id)
    SELECT 1, r.id FROM dev_e_commerce.role r WHERE r.role_key = 'ADMIN';
INSERT INTO dev_e_commerce.user_role (user_id, role_id)
    SELECT 21, r.id FROM dev_e_commerce.role r WHERE r.role_key = 'USER';
INSERT INTO dev_e_commerce.user_role (user_id, role_id)
    SELECT 41, r.id FROM dev_e_commerce.role r WHERE r.role_key = 'USER';

COMMIT;
