CREATE TABLE IF NOT EXISTS shopping_cart (
  laptop_id BIGINT PRIMARY KEY,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_shopping_cart_laptop FOREIGN KEY (laptop_id) REFERENCES laptop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
