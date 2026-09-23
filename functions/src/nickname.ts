export const NICKNAME_RE = /^[가-힣a-zA-Z0-9]{2,12}$/;
export const COLOR_COUNT = 7;

export function isValidNickname(s: unknown): s is string {
  return typeof s === 'string' && NICKNAME_RE.test(s);
}
