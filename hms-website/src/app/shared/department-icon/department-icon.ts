/**
 * PublicDepartment carries only {id, name} - there's no icon field on the
 * backend/CMS. Maps a department's real name to one of IconSpriteComponent's
 * symbols by keyword, falling back to a generic stethoscope so every
 * department (including ones this list doesn't anticipate) still gets a
 * sensible icon rather than a blank badge.
 */
const KEYWORD_ICON_MAP: Array<[RegExp, string]> = [
  [/cardio|heart/i, 'heart-pulse'],
  [/neuro|brain/i, 'brain'],
  [/ortho|bone|spine/i, 'bone'],
  [/pediatric|paediatric|child/i, 'baby'],
  [/dental|tooth|oral/i, 'tooth'],
  [/derma|skin/i, 'dermatology'],
  [/\bent\b|ear|nose|throat/i, 'ent'],
  [/ophthal|eye|vision/i, 'eye'],
  [/gyneco|obstetric|maternity|women/i, 'gynecology'],
  [/emergency|trauma|critical/i, 'ambulance']
];

export function departmentIcon(name: string): string {
  const match = KEYWORD_ICON_MAP.find(([pattern]) => pattern.test(name));
  return match ? match[1] : 'stetho';
}
