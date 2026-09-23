import { isValidNickname } from '../src/nickname';

test('한글·영문·숫자 2~12자만 허용', () => {
  expect(isValidNickname('땅주인')).toBe(true);
  expect(isValidNickname('ab')).toBe(true);
  expect(isValidNickname('a')).toBe(false);
  expect(isValidNickname('열세글자넘는닉네임입니다요')).toBe(false);
  expect(isValidNickname('공백 있음')).toBe(false);
  expect(isValidNickname(null)).toBe(false);
});
