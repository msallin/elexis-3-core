#
# SQL init for ch.elexis.data.Role
# MEDEVIT <office@medevit.at>
#
# See https://redmine.medelexis.ch/issues/2112
# Roles and rights are tightly interdependent, hence we initialize
# all tables within one SQL file
#
CREATE TABLE ROLE (
  ID VARCHAR(25) NOT NULL,
  LASTUPDATE BIGINT DEFAULT NULL,
  DELETED CHAR(1) DEFAULT '0',
  EXTINFO BLOB,
  ISSYSTEMROLE CHAR(1) DEFAULT '0',
  PRIMARY KEY (ID)
);
 
INSERT INTO ROLE (ID, ISSYSTEMROLE) VALUES ('user', '1');
INSERT INTO ROLE (ID, ISSYSTEMROLE) VALUES ('user_external', '1');
INSERT INTO ROLE (ID, ISSYSTEMROLE) VALUES ('executive_doctor', '1');
INSERT INTO ROLE (ID, ISSYSTEMROLE) VALUES ('doctor', '1');
INSERT INTO ROLE (ID, ISSYSTEMROLE) VALUES ('assistant', '1');
INSERT INTO ROLE (ID, ISSYSTEMROLE) VALUES ('patient', '1');

CREATE TABLE USER_ROLE_JOINT (
  ID VARCHAR(25) NOT NULL,
  LASTUPDATE BIGINT DEFAULT NULL,
  DELETED CHAR(1) DEFAULT NULL,
  USER_ID VARCHAR(25) NOT NULL,
  PRIMARY KEY (ID, USER_ID)
);

# We must not name the table RIGHT, as this is an SQL keyword
CREATE TABLE RIGHT_ (
  ID VARCHAR(25) NOT NULL, 				# from ACE#getUniqueHashFromACE()
  LASTUPDATE BIGINT DEFAULT NULL,
  DELETED CHAR(1) DEFAULT '0',
  LOG_EXECUTION CHAR(1),
  NAME VARCHAR(256),
  PARENTID VARCHAR(25),
  I18N_NAME VARCHAR(256),
  PRIMARY KEY (ID)
);

INSERT INTO RIGHT_ (ID, NAME, PARENTID) VALUES ('root', 'root', '');

# Join the roles with the rights
CREATE TABLE ROLE_RIGHT_JOINT (
  ID VARCHAR(25) NOT NULL,
  LASTUPDATE BIGINT DEFAULT NULL,
  DELETED CHAR(1) DEFAULT NULL,
  ROLE_ID VARCHAR(25) NOT NULL,
  PRIMARY KEY (ID, ROLE_ID)
);

# We prepare a view to join the rights of a user for easy querying
CREATE OR REPLACE VIEW RIGHTS_PER_ROLE AS SELECT 
	r.ID AS ROLE_ID, ri.ID AS RIGHT_ID
FROM 
	ROLE r
	LEFT JOIN ROLE_RIGHT_JOINT rrj
		ON r.ID = rrj.ROLE_ID
	LEFT JOIN RIGHT_ ri
		ON rrj.ID = ri.ID
ORDER BY r.ID;

# We prepare a view to join the rights of a user for easy querying
CREATE OR REPLACE VIEW RIGHTS_PER_USER AS SELECT 
	u.ID AS USER_ID, ri.ID AS RIGHT_ID, ri.NAME AS RIGHT_NAME
FROM 
	USER_ u
	LEFT JOIN USER_ROLE_JOINT urj 
		ON u.ID = urj.USER_ID
	LEFT JOIN ROLE r
		ON urj.ID = r.ID
	LEFT JOIN ROLE_RIGHT_JOINT rrj
		ON r.ID = rrj.ROLE_ID
	LEFT JOIN RIGHT_ ri
		ON rrj.ID = ri.ID
ORDER BY u.ID;


# DROP TABLE ROLE;
# DROP TABLE USER_ROLE_JOINT;
# DROP TABLE RIGHT_;
# DROP TABLE ROLE_RIGHT_JOINT;
# DROP VIEW RIGHTS_PER_ROLE;
# DROP VIEW RIGHTS_PER_USER;