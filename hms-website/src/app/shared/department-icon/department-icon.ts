/**
 * PublicDepartment carries only {id, name} - there's no icon or color field on
 * the backend/CMS. Maps a department's real name to one of IconSpriteComponent's
 * symbols AND a distinct accent hue by keyword, falling back to a generic
 * stethoscope/teal so every department (including ones this list doesn't
 * anticipate) still gets a sensible, on-brand look rather than a blank badge.
 *
 * Color is deliberately per-specialty rather than per-item-index: a hospital's
 * department list is fairly stable, so keying off the name means Cardiology
 * is always rose, Neurology always violet, etc. - consistent across the
 * Departments grid, the Doctors grid (via department name), and anywhere else
 * this is reused, rather than shifting if items are reordered.
 */
export interface DepartmentVisual {
  icon: string;
  color: string;
}

const DEFAULT_VISUAL: DepartmentVisual = { icon: 'stetho', color: '#0f6e5f' };

const KEYWORD_VISUAL_MAP: Array<[RegExp, DepartmentVisual]> = [
  [/cardio|heart/i, { icon: 'heart-pulse', color: '#e0475f' }],
  [/neuro|brain/i, { icon: 'brain', color: '#7c5cd4' }],
  [/ortho|bone|spine/i, { icon: 'bone', color: '#d98a2b' }],
  [/pediatric|paediatric|child/i, { icon: 'baby', color: '#2f9bd9' }],
  [/dental|tooth|oral/i, { icon: 'tooth', color: '#17a398' }],
  [/derma|skin/i, { icon: 'dermatology', color: '#d9548f' }],
  [/\bent\b|ear|nose|throat/i, { icon: 'ent', color: '#5a63c7' }],
  [/ophthal|eye|vision/i, { icon: 'eye', color: '#2f6fd9' }],
  [/gyneco|obstetric|maternity|women/i, { icon: 'gynecology', color: '#b23d8f' }],
  [/emergency|trauma|critical/i, { icon: 'ambulance', color: '#d9432f' }],
  [/general|family/i, { icon: 'stetho', color: DEFAULT_VISUAL.color }]
];

export function departmentVisual(name: string): DepartmentVisual {
  const match = KEYWORD_VISUAL_MAP.find(([pattern]) => pattern.test(name));
  return match ? match[1] : DEFAULT_VISUAL;
}

export function departmentIcon(name: string): string {
  return departmentVisual(name).icon;
}

export function departmentColor(name: string): string {
  return departmentVisual(name).color;
}
