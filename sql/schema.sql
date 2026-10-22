-- Run ONCE in a fresh local demo database. Does not drop existing tables.
CREATE DATABASE IF NOT EXISTS serc;
USE serc;
CREATE TABLE users (
 user_id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL, contact VARCHAR(100)
) ENGINE=InnoDB;
CREATE TABLE response_teams (
 team_id VARCHAR(36) PRIMARY KEY, type VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL, x_coord DOUBLE NOT NULL, y_coord DOUBLE NOT NULL
) ENGINE=InnoDB;
CREATE TABLE emergencies (
 emergency_id VARCHAR(36) PRIMARY KEY, type VARCHAR(20) NOT NULL,
 severity INT NOT NULL CHECK(severity BETWEEN 1 AND 5), location VARCHAR(200) NOT NULL,
 x_coord DOUBLE NOT NULL, y_coord DOUBLE NOT NULL,
 reported_by VARCHAR(36) NOT NULL, reported_at DATETIME(6) NOT NULL,
 queue_sequence BIGINT NOT NULL UNIQUE, status VARCHAR(20) NOT NULL,
 assigned_team_id VARCHAR(36),
 FOREIGN KEY(reported_by) REFERENCES users(user_id),
 FOREIGN KEY(assigned_team_id) REFERENCES response_teams(team_id)
) ENGINE=InnoDB;
CREATE TABLE response_history (
 history_id VARCHAR(36) PRIMARY KEY, emergency_id VARCHAR(36) NOT NULL UNIQUE,
 team_id VARCHAR(36) NOT NULL, assigned_at DATETIME(6) NOT NULL,
 started_at DATETIME(6), resolved_at DATETIME(6),
 FOREIGN KEY(emergency_id) REFERENCES emergencies(emergency_id),
 FOREIGN KEY(team_id) REFERENCES response_teams(team_id)
) ENGINE=InnoDB;
-- Fifth, internal table protects full-snapshot writes from stale sessions.
CREATE TABLE app_metadata (
 id INT PRIMARY KEY, revision BIGINT NOT NULL,
 next_sequence BIGINT NOT NULL, saved_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;
INSERT INTO app_metadata VALUES (1,0,1,CURRENT_TIMESTAMP(6));
