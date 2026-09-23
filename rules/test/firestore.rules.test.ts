import { readFileSync } from 'fs';
import { resolve } from 'path';
import {
  assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, serverTimestamp, setDoc, updateDoc, Timestamp } from 'firebase/firestore';

let env: RulesTestEnvironment;
const rules = readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8');

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-eat-the-land',
    firestore: { rules, host: '127.0.0.1', port: 8080 },
  });
});
afterAll(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();

const profile = (over: Record<string, unknown> = {}) => ({
  nickname: '땅주인', nicknameLower: '땅주인', color: 3, cellCount: 0, createdAt: serverTimestamp(), ...over,
});

async function seedUser(uid: string, over: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', uid), {
      ...profile({ nickname: uid, nicknameLower: uid }), createdAt: Timestamp.now(), ...over,
    });
  });
}

describe('users', () => {
  test('본인이 유효한 프로필을 만들 수 있다', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'), profile()));
  });
  test('비로그인·타인 uid·잘못된 닉네임·lower 불일치·색 범위·cellCount≠0·createdAt≠서버시각 은 거부', async () => {
    await assertFails(setDoc(doc(anon(), 'users/alice'), profile()));
    await assertFails(setDoc(doc(bob(), 'users/alice'), profile()));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ nickname: 'a' })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ nickname: 'Walker', nicknameLower: 'Walker' })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ color: 7 })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ cellCount: 1 })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ createdAt: Timestamp.now() })));
  });
  test('닉네임 변경은 본인만, 다른 필드는 못 건드린다', async () => {
    await seedUser('alice');
    await assertSucceeds(updateDoc(doc(alice(), 'users/alice'), { nickname: '새이름', nicknameLower: '새이름' }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { nickname: '해킹', nicknameLower: '해킹' }));
    await assertFails(updateDoc(doc(alice(), 'users/alice'), { color: 1 }));
  });
  test('cellCount 는 누구나 정확히 ±1 만', async () => {
    await seedUser('alice', { cellCount: 5 });
    await assertSucceeds(updateDoc(doc(bob(), 'users/alice'), { cellCount: 4 }));
    await assertSucceeds(updateDoc(doc(bob(), 'users/alice'), { cellCount: 5 }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { cellCount: 7 }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { cellCount: 5, color: 2 }));
    await seedUser('carol', { cellCount: 0 });
    await assertFails(updateDoc(doc(bob(), 'users/carol'), { cellCount: -1 }));
  });
  test('삭제는 본인만', async () => {
    await seedUser('alice');
    await assertFails(deleteDoc(doc(bob(), 'users/alice')));
    await assertSucceeds(deleteDoc(doc(alice(), 'users/alice')));
  });
});

describe('nicknames', () => {
  test('생성은 본인 uid 로만, 이미 있으면 거부(유일성)', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'nicknames/땅주인'), { uid: 'alice' }));
    await assertFails(setDoc(doc(bob(), 'nicknames/땅주인'), { uid: 'bob' }));
    await assertFails(setDoc(doc(bob(), 'nicknames/다른이름'), { uid: 'alice' }));
  });
  test('삭제는 소유자만', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'nicknames/땅주인'), { uid: 'alice' }));
    await assertFails(deleteDoc(doc(bob(), 'nicknames/땅주인')));
    await assertSucceeds(deleteDoc(doc(alice(), 'nicknames/땅주인')));
  });
});

describe('cells', () => {
  const cell = (uid: string, over: Record<string, unknown> = {}) => ({
    ownerUid: uid, ownerColor: 2, capturedAt: serverTimestamp(), region: '872ab', ...over,
  });
  test('본인 소유로 생성·뺏기(update) 가능, 타인 uid·클라 시각·삭제는 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'cells/8b2a'), cell('alice')));
    await assertSucceeds(setDoc(doc(bob(), 'cells/8b2a'), cell('bob')));
    await assertFails(setDoc(doc(bob(), 'cells/8b2b'), cell('alice')));
    await assertFails(setDoc(doc(bob(), 'cells/8b2c'), cell('bob', { capturedAt: Timestamp.now() })));
    await assertFails(setDoc(doc(bob(), 'cells/8b2d'), cell('bob', { extra: 1 })));
    await assertFails(deleteDoc(doc(bob(), 'cells/8b2a')));
    await assertFails(setDoc(doc(anon(), 'cells/8b2e'), cell('anon')));
  });
});
