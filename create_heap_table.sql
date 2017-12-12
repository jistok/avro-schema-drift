-- This is version 1 of the DDL
DROP TABLE IF EXISTS public.crimes;
CREATE TABLE public.crimes
(
  id   INT   NOT NULL,
  case_number   TEXT,
  crime_date   TEXT,
  block   TEXT,
  iucr   TEXT,
  primary_type   TEXT,
  description   TEXT,
  location_desc   TEXT,
  arrest   BOOL,
  domestic   BOOL,
  beat   TEXT,
  district   TEXT,
  ward   INT,
  community_area   TEXT,
  fbi_code   TEXT,
  x_coord   FLOAT4,
  y_coord   FLOAT4
)
DISTRIBUTED BY (id);

