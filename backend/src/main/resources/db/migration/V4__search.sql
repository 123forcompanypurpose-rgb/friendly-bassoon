CREATE OR REPLACE FUNCTION stories_search_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.blurb, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stories_search
BEFORE INSERT OR UPDATE OF title, blurb ON stories
FOR EACH ROW EXECUTE FUNCTION stories_search_update();
