/**
 * Shared semantic tone vocabulary for every tone-based UI primitive
 * (StatTileComponent, StatusBadgeComponent, SectionCardComponent, ...) so a
 * new "green means success" style decision is made once, here, rather than
 * redeclared per component with its own local union.
 */
export type Tone = 'primary' | 'secondary' | 'tertiary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';
