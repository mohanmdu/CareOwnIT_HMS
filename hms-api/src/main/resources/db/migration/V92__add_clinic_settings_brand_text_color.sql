-- Independent color for the hospital name shown top-left in the side menu
-- (see app-shell.component.scss's .shell-brand) - previously hardcoded to
-- the theme's primary color, so a clinic whose brand color didn't read well
-- as small header text had no way to fix just that without recoloring every
-- other primary-colored element too.
ALTER TABLE clinic_settings ADD COLUMN brand_text_color VARCHAR(9) NULL;
