CREATE TABLE IF NOT EXISTS recipes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cuisine VARCHAR(100),
  title VARCHAR(255),
  rating DOUBLE,
  prep_time INT,
  cook_time INT,
  total_time INT,
  description TEXT,
  nutrients TEXT,
  serves VARCHAR(50)
);

CREATE INDEX idx_recipes_rating ON recipes (rating);
CREATE INDEX idx_recipes_cuisine ON recipes (cuisine);
CREATE INDEX idx_recipes_total_time ON recipes (total_time);
