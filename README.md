# discordBotTest

DB Schemas:

.schema queuetypes
CREATE TABLE queuetypes (id INTEGER PRIMARY KEY, map TEXT NOT NULL, description TEXT NOT NULL, notes TEXT);

sqlite> .schema champlist
CREATE TABLE champlist (champ_id INTEGER PRIMARY KEY, champ_name text NOT NULL);