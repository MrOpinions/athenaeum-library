-- Library Management System schema (MySQL 8).
-- Statements are separated by a line containing only "-- @@" so triggers/procedures
-- (which contain semicolons) can be sent through plain JDBC without a DELIMITER command.

CREATE TABLE plans (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(40)  NOT NULL UNIQUE,
  max_loans     INT          NOT NULL CHECK (max_loans > 0),
  loan_days     INT          NOT NULL CHECK (loan_days > 0),
  max_renewals  INT          NOT NULL DEFAULT 2 CHECK (max_renewals >= 0),
  fine_per_day  DECIMAL(6,2) NOT NULL DEFAULT 0.50 CHECK (fine_per_day >= 0),
  annual_fee    DECIMAL(8,2) NOT NULL DEFAULT 0
) ENGINE=InnoDB
-- @@
CREATE TABLE users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(120) NOT NULL UNIQUE,
  name          VARCHAR(100) NOT NULL,
  password_hash VARCHAR(200) NOT NULL,
  role          ENUM('ADMIN','LIBRARIAN','MEMBER') NOT NULL DEFAULT 'MEMBER',
  plan_id       INT          NOT NULL,
  card_no       CHAR(10)     NOT NULL UNIQUE,
  phone         VARCHAR(20),
  joined_on     DATE         NOT NULL,
  expires_on    DATE         NOT NULL,
  status        ENUM('ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT fk_users_plan FOREIGN KEY (plan_id) REFERENCES plans(id)
) ENGINE=InnoDB
-- @@
CREATE TABLE sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id    INT      NOT NULL,
  expires_at DATETIME NOT NULL,
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB
-- @@
CREATE TABLE categories (
  id    INT AUTO_INCREMENT PRIMARY KEY,
  name  VARCHAR(60) NOT NULL UNIQUE,
  color CHAR(7)     NOT NULL DEFAULT '#8b7d6b'
) ENGINE=InnoDB
-- @@
CREATE TABLE authors (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB
-- @@
CREATE TABLE books (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  isbn           CHAR(13)     NOT NULL UNIQUE,
  title          VARCHAR(200) NOT NULL,
  publisher      VARCHAR(100),
  published_year SMALLINT CHECK (published_year BETWEEN 1000 AND 2100),
  category_id    INT          NOT NULL,
  language       VARCHAR(30)  NOT NULL DEFAULT 'English',
  pages          INT CHECK (pages > 0),
  description    TEXT,
  added_on       DATE         NOT NULL,
  INDEX idx_books_title (title),
  INDEX idx_books_category (category_id),
  CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB
-- @@
CREATE TABLE book_authors (
  book_id   INT NOT NULL,
  author_id INT NOT NULL,
  PRIMARY KEY (book_id, author_id),
  CONSTRAINT fk_ba_book   FOREIGN KEY (book_id)   REFERENCES books(id)   ON DELETE CASCADE,
  CONSTRAINT fk_ba_author FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
) ENGINE=InnoDB
-- @@
CREATE TABLE copies (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  book_id     INT         NOT NULL,
  barcode     VARCHAR(20) NOT NULL UNIQUE,
  shelf       VARCHAR(20),
  status      ENUM('AVAILABLE','ISSUED','ON_HOLD','LOST','DAMAGED','WITHDRAWN') NOT NULL DEFAULT 'AVAILABLE',
  price       DECIMAL(8,2) NOT NULL DEFAULT 0,
  acquired_on DATE,
  INDEX idx_copies_book_status (book_id, status),
  CONSTRAINT fk_copies_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
) ENGINE=InnoDB
-- @@
CREATE TABLE loans (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  copy_id     INT      NOT NULL,
  user_id     INT      NOT NULL,
  issued_at   DATETIME NOT NULL,
  due_at      DATETIME NOT NULL,
  returned_at DATETIME NULL,
  renewals    INT      NOT NULL DEFAULT 0,
  issued_by   INT      NULL,
  INDEX idx_loans_user (user_id, returned_at),
  INDEX idx_loans_copy (copy_id, returned_at),
  INDEX idx_loans_issued (issued_at),
  CONSTRAINT chk_loan_dates CHECK (due_at > issued_at),
  CONSTRAINT fk_loans_copy FOREIGN KEY (copy_id)   REFERENCES copies(id),
  CONSTRAINT fk_loans_user FOREIGN KEY (user_id)   REFERENCES users(id),
  CONSTRAINT fk_loans_by   FOREIGN KEY (issued_by) REFERENCES users(id)
) ENGINE=InnoDB
-- @@
CREATE TABLE fines (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  loan_id    INT          NOT NULL,
  user_id    INT          NOT NULL,
  amount     DECIMAL(8,2) NOT NULL CHECK (amount > 0),
  reason     ENUM('OVERDUE','LOST','DAMAGE') NOT NULL,
  status     ENUM('UNPAID','PAID','WAIVED') NOT NULL DEFAULT 'UNPAID',
  created_at DATETIME     NOT NULL,
  paid_at    DATETIME     NULL,
  method     VARCHAR(20)  NULL,
  INDEX idx_fines_user (user_id, status),
  CONSTRAINT fk_fines_loan FOREIGN KEY (loan_id) REFERENCES loans(id),
  CONSTRAINT fk_fines_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB
-- @@
CREATE TABLE reservations (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  book_id    INT      NOT NULL,
  user_id    INT      NOT NULL,
  created_at DATETIME NOT NULL,
  status     ENUM('WAITING','READY','FULFILLED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'WAITING',
  ready_at   DATETIME NULL,
  expires_at DATETIME NULL,
  copy_id    INT      NULL,
  INDEX idx_res_book (book_id, status),
  INDEX idx_res_user (user_id, status),
  CONSTRAINT fk_res_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_res_copy FOREIGN KEY (copy_id) REFERENCES copies(id) ON DELETE SET NULL
) ENGINE=InnoDB
-- @@
CREATE TABLE reviews (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  book_id    INT      NOT NULL,
  user_id    INT      NOT NULL,
  rating     TINYINT  NOT NULL CHECK (rating BETWEEN 1 AND 5),
  body       VARCHAR(1000),
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_review (book_id, user_id),
  CONSTRAINT fk_rev_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  CONSTRAINT fk_rev_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB
-- @@
CREATE TABLE favorites (
  user_id INT NOT NULL,
  book_id INT NOT NULL,
  PRIMARY KEY (user_id, book_id),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_fav_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
) ENGINE=InnoDB
-- @@
CREATE TABLE book_requests (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  user_id    INT          NOT NULL,
  title      VARCHAR(200) NOT NULL,
  author     VARCHAR(100),
  note       VARCHAR(500),
  status     ENUM('PENDING','APPROVED','REJECTED','PURCHASED') NOT NULL DEFAULT 'PENDING',
  created_at DATETIME     NOT NULL,
  decided_by INT          NULL,
  CONSTRAINT fk_req_user FOREIGN KEY (user_id)    REFERENCES users(id),
  CONSTRAINT fk_req_by   FOREIGN KEY (decided_by) REFERENCES users(id)
) ENGINE=InnoDB
-- @@
CREATE TABLE notifications (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  user_id    INT          NOT NULL,
  kind       VARCHAR(20)  NOT NULL,
  message    VARCHAR(300) NOT NULL,
  created_at DATETIME     NOT NULL,
  read_at    DATETIME     NULL,
  ref_key    VARCHAR(60)  NULL,
  UNIQUE KEY uq_notif_ref (user_id, ref_key),
  INDEX idx_notif_user (user_id, read_at),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB
-- @@
CREATE TABLE audit_log (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  ts_ms     BIGINT       NOT NULL,
  actor_id  INT          NULL,
  action    VARCHAR(30)  NOT NULL,
  detail    VARCHAR(500) NOT NULL,
  prev_hash CHAR(64)     NOT NULL,
  hash      CHAR(64)     NOT NULL
) ENGINE=InnoDB
-- @@
CREATE VIEW v_book_stats AS
SELECT b.id AS book_id,
  (SELECT COUNT(*) FROM copies c WHERE c.book_id = b.id AND c.status NOT IN ('LOST','WITHDRAWN')) AS total_copies,
  (SELECT COUNT(*) FROM copies c WHERE c.book_id = b.id AND c.status = 'AVAILABLE')               AS available_copies,
  (SELECT ROUND(AVG(r.rating), 1) FROM reviews r WHERE r.book_id = b.id)                          AS avg_rating,
  (SELECT COUNT(*) FROM reviews r WHERE r.book_id = b.id)                                         AS review_count,
  (SELECT COUNT(*) FROM loans l JOIN copies c ON c.id = l.copy_id WHERE c.book_id = b.id)         AS times_borrowed
FROM books b
-- @@
CREATE VIEW v_overdue AS
SELECT l.id AS loan_id, l.user_id, u.name AS member, u.card_no, cp.book_id, b.title, cp.barcode, l.due_at,
  DATEDIFF(CURDATE(), DATE(l.due_at)) AS days_overdue,
  ROUND(DATEDIFF(CURDATE(), DATE(l.due_at)) * p.fine_per_day, 2) AS accrued
FROM loans l
JOIN users u   ON u.id = l.user_id
JOIN plans p   ON p.id = u.plan_id
JOIN copies cp ON cp.id = l.copy_id
JOIN books b   ON b.id = cp.book_id
WHERE l.returned_at IS NULL AND l.due_at < NOW()
-- @@
CREATE TRIGGER trg_loans_bi BEFORE INSERT ON loans FOR EACH ROW
BEGIN
  DECLARE s  VARCHAR(20);
  DECLARE us VARCHAR(20);
  IF NEW.returned_at IS NULL THEN
    SELECT status INTO s FROM copies WHERE id = NEW.copy_id;
    IF s <> 'AVAILABLE' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This copy is not available for loan';
    END IF;
    SELECT status INTO us FROM users WHERE id = NEW.user_id;
    IF us <> 'ACTIVE' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Member account is suspended';
    END IF;
  END IF;
END
-- @@
CREATE TRIGGER trg_loans_ai AFTER INSERT ON loans FOR EACH ROW
BEGIN
  IF NEW.returned_at IS NULL THEN
    UPDATE copies SET status = 'ISSUED' WHERE id = NEW.copy_id;
  END IF;
END
-- @@
CREATE TRIGGER trg_loans_au AFTER UPDATE ON loans FOR EACH ROW
BEGIN
  IF OLD.returned_at IS NULL AND NEW.returned_at IS NOT NULL THEN
    UPDATE copies SET status = 'AVAILABLE' WHERE id = NEW.copy_id AND status = 'ISSUED';
  END IF;
END
-- @@
CREATE PROCEDURE sp_purge_sessions()
BEGIN
  DELETE FROM sessions WHERE expires_at < NOW();
END
