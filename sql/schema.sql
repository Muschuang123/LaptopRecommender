CREATE TABLE IF NOT EXISTS brand (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL UNIQUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cpu_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  brand VARCHAR(64),
  model VARCHAR(255) NOT NULL UNIQUE,
  core_count INT,
  thread_count INT,
  base_power_w DECIMAL(8,2),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gpu_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  brand VARCHAR(64),
  model VARCHAR(255) NOT NULL UNIQUE,
  gpu_type VARCHAR(32),
  vram_gb INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS memory_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  capacity_gb INT,
  memory_type VARCHAR(64),
  frequency_mhz INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS storage_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  capacity_gb INT,
  storage_type VARCHAR(64),
  interface_type VARCHAR(128),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS screen_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  size_inch DECIMAL(5,2),
  resolution VARCHAR(64),
  refresh_rate_hz INT,
  panel_type VARCHAR(64),
  color_gamut_percent INT,
  brightness_nit INT,
  touch_support TINYINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS battery_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  capacity_wh INT,
  charge_power VARCHAR(128),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wireless_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  wifi_version VARCHAR(255),
  bluetooth_version VARCHAR(128),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS port_spec (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  port_name VARCHAR(128) NOT NULL UNIQUE,
  port_type VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS laptop (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  brand_id BIGINT NOT NULL,
  cpu_id BIGINT,
  gpu_id BIGINT,
  memory_id BIGINT,
  storage_id BIGINT,
  screen_id BIGINT,
  battery_id BIGINT,
  wireless_id BIGINT,
  model VARCHAR(255) NOT NULL,
  product_type VARCHAR(128),
  usage_positioning VARCHAR(128),
  weight_kg DECIMAL(6,3),
  thickness_mm DECIMAL(8,2),
  os VARCHAR(255),
  color VARCHAR(128),
  image_url VARCHAR(768),
  source_url VARCHAR(768),
  source_name VARCHAR(64),
  raw_title VARCHAR(512),
  release_date DATE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_laptop_brand_model (brand_id, model),
  UNIQUE KEY uk_laptop_source_url (source_url),
  CONSTRAINT fk_laptop_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_laptop_cpu FOREIGN KEY (cpu_id) REFERENCES cpu_spec(id),
  CONSTRAINT fk_laptop_gpu FOREIGN KEY (gpu_id) REFERENCES gpu_spec(id),
  CONSTRAINT fk_laptop_memory FOREIGN KEY (memory_id) REFERENCES memory_spec(id),
  CONSTRAINT fk_laptop_storage FOREIGN KEY (storage_id) REFERENCES storage_spec(id),
  CONSTRAINT fk_laptop_screen FOREIGN KEY (screen_id) REFERENCES screen_spec(id),
  CONSTRAINT fk_laptop_battery FOREIGN KEY (battery_id) REFERENCES battery_spec(id),
  CONSTRAINT fk_laptop_wireless FOREIGN KEY (wireless_id) REFERENCES wireless_spec(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS laptop_port (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  laptop_id BIGINT NOT NULL,
  port_id BIGINT NOT NULL,
  port_count INT DEFAULT 1,
  UNIQUE KEY uk_laptop_port (laptop_id, port_id),
  CONSTRAINT fk_laptop_port_laptop FOREIGN KEY (laptop_id) REFERENCES laptop(id),
  CONSTRAINT fk_laptop_port_port FOREIGN KEY (port_id) REFERENCES port_spec(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS price_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  laptop_id BIGINT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  source_name VARCHAR(64),
  source_url VARCHAR(768),
  crawled_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_price_laptop FOREIGN KEY (laptop_id) REFERENCES laptop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_name VARCHAR(64) NOT NULL UNIQUE,
  base_url VARCHAR(512) NOT NULL,
  enabled TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT,
  fetched_count INT DEFAULT 0,
  inserted_count INT DEFAULT 0,
  updated_count INT DEFAULT 0,
  status VARCHAR(64),
  message TEXT,
  started_at DATETIME,
  finished_at DATETIME,
  CONSTRAINT fk_crawl_log_source FOREIGN KEY (source_id) REFERENCES crawl_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_laptop_brand ON laptop(brand_id);
CREATE INDEX idx_laptop_cpu ON laptop(cpu_id);
CREATE INDEX idx_laptop_gpu ON laptop(gpu_id);
CREATE INDEX idx_laptop_memory ON laptop(memory_id);
CREATE INDEX idx_laptop_storage ON laptop(storage_id);
CREATE INDEX idx_laptop_screen ON laptop(screen_id);
CREATE INDEX idx_laptop_weight ON laptop(weight_kg);
CREATE INDEX idx_price_laptop_time ON price_record(laptop_id, crawled_at);
