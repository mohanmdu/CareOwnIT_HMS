-- Independent color for a side-menu icon on hover (see app-shell.component.scss's
-- .shell-nav-list a.mat-mdc-list-item:hover rule) - previously the icon kept
-- its static menu_icon_color even while hovering, since only the background
-- and label text had a distinct hover color.
ALTER TABLE clinic_settings ADD COLUMN menu_hover_icon_color VARCHAR(9) NULL;
