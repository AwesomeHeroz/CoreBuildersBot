CREATE TABLE marketplace_shops (
    id CHAR(36) PRIMARY KEY,
    owner_member_id CHAR(36) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_marketplace_shop_owner FOREIGN KEY (owner_member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketplace_items (
    id CHAR(36) PRIMARY KEY,
    shop_id CHAR(36) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    image_url VARCHAR(1000),
    stock INT NOT NULL DEFAULT 0,
    price BIGINT NOT NULL,
    category VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_marketplace_item_shop FOREIGN KEY (shop_id) REFERENCES marketplace_shops(id) ON DELETE CASCADE,
    CONSTRAINT chk_marketplace_item_stock CHECK (stock >= 0),
    CONSTRAINT chk_marketplace_item_price CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_marketplace_item_public ON marketplace_items(active, category, price, name);
CREATE INDEX idx_marketplace_item_shop ON marketplace_items(shop_id, active, updated_at);

CREATE TABLE marketplace_carts (
    id CHAR(36) PRIMARY KEY,
    member_id CHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_marketplace_cart_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketplace_cart_items (
    cart_id CHAR(36) NOT NULL,
    item_id CHAR(36) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (cart_id, item_id),
    CONSTRAINT fk_marketplace_cart_item_cart FOREIGN KEY (cart_id) REFERENCES marketplace_carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_marketplace_cart_item_item FOREIGN KEY (item_id) REFERENCES marketplace_items(id) ON DELETE CASCADE,
    CONSTRAINT chk_marketplace_cart_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketplace_orders (
    id CHAR(36) PRIMARY KEY,
    buyer_member_id CHAR(36) NOT NULL,
    total_price BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PAID',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_marketplace_order_buyer FOREIGN KEY (buyer_member_id) REFERENCES members(id),
    CONSTRAINT chk_marketplace_order_total CHECK (total_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_marketplace_order_buyer ON marketplace_orders(buyer_member_id, created_at);

CREATE TABLE marketplace_order_items (
    id CHAR(36) PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    item_id CHAR(36) NOT NULL,
    shop_id CHAR(36) NOT NULL,
    seller_member_id CHAR(36) NOT NULL,
    shop_name VARCHAR(120) NOT NULL,
    item_name VARCHAR(150) NOT NULL,
    image_url VARCHAR(1000),
    category VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    unit_price BIGINT NOT NULL,
    line_total BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_DELIVERY',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delivered_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_marketplace_order_item_order FOREIGN KEY (order_id) REFERENCES marketplace_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_marketplace_order_item_product FOREIGN KEY (item_id) REFERENCES marketplace_items(id),
    CONSTRAINT fk_marketplace_order_item_shop FOREIGN KEY (shop_id) REFERENCES marketplace_shops(id),
    CONSTRAINT fk_marketplace_order_item_seller FOREIGN KEY (seller_member_id) REFERENCES members(id),
    CONSTRAINT chk_marketplace_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_marketplace_order_item_prices CHECK (unit_price > 0 AND line_total > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_marketplace_sales ON marketplace_order_items(seller_member_id, status, created_at);
CREATE INDEX idx_marketplace_order_lines ON marketplace_order_items(order_id, created_at);
