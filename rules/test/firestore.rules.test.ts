import { readFileSync } from 'fs';
import { resolve } from 'path';
import {
  assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc, doc, Firestore, getDoc, serverTimestamp, setDoc, updateDoc, Timestamp, writeBatch,
} from 'firebase/firestore';

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

// rules-unit-testing 의 compat Firestore 를 modular API 에 넘기기 위한 형 변환
const as = (uid: string) => env.authenticatedContext(uid).firestore() as unknown as Firestore;
const alice = () => as('alice');
const bob = () => as('bob');
const anon = () => env.unauthenticatedContext().firestore() as unknown as Firestore;

const profile = (over: Record<string, unknown> = {}) => ({
  nickname: '땅주인', nicknameLower: '땅주인', color: 3, cellCount: 0, createdAt: serverTimestamp(), ...over,
});

/** 규칙을 끄고 프로필과 닉네임 예약을 짝으로 심는다. */
async function seedUser(uid: string, over: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore() as unknown as Firestore;
    const data = { ...profile({ nickname: uid, nicknameLower: uid }), createdAt: Timestamp.now(), ...over };
    await setDoc(doc(db, 'users', uid), data);
    await setDoc(doc(db, 'nicknames', data.nicknameLower as string), { uid });
  });
}

/** 앱의 setNickname 트랜잭션(신규 가입)과 같은 쓰기 묶음. */
function signUp(db: Firestore, uid: string, over: Record<string, unknown> = {}) {
  const data = profile(over);
  const batch = writeBatch(db);
  batch.set(doc(db, 'users', uid), data);
  batch.set(doc(db, 'nicknames', data.nicknameLower as string), { uid });
  return batch.commit();
}

describe('users', () => {
  test('본인이 프로필과 닉네임 예약을 한 묶음으로 만들 수 있다', async () => {
    await assertSucceeds(signUp(alice(), 'alice'));
  });
  test('닉네임 예약 없이 프로필만 만들 수 없다', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile()));
  });
  test('남이 예약한 닉네임으로 프로필을 만들거나 바꿀 수 없다', async () => {
    await seedUser('alice', { nickname: 'Walker', nicknameLower: 'walker' });
    await assertFails(setDoc(doc(bob(), 'users/bob'), profile({ nickname: 'Walker', nicknameLower: 'walker' })));
    await assertFails(setDoc(doc(bob(), 'users/bob'), profile({ nickname: 'walker', nicknameLower: 'walker' })));
    await seedUser('bob');
    await assertFails(updateDoc(doc(bob(), 'users/bob'), { nickname: 'Walker', nicknameLower: 'walker' }));
  });
  test('비로그인·타인 uid·잘못된 닉네임·lower 불일치·색 범위·cellCount≠0·createdAt≠서버시각 은 거부', async () => {
    await assertFails(signUp(anon(), 'alice'));
    await assertFails(signUp(bob(), 'alice'));
    await assertFails(signUp(alice(), 'alice', { nickname: 'a', nicknameLower: 'a' }));
    await assertFails(signUp(alice(), 'alice', { nickname: 'Walker', nicknameLower: 'Walker' }));
    await assertFails(signUp(alice(), 'alice', { color: 7 }));
    await assertFails(signUp(alice(), 'alice', { cellCount: 1 }));
    await assertFails(signUp(alice(), 'alice', { createdAt: Timestamp.now() }));
    await assertFails(signUp(alice(), 'alice', { extra: 1 }));
    await assertFails(signUp(alice(), 'alice', { color: '3' }));
  });
  test('닉네임 변경은 옛 예약 삭제·프로필 갱신·새 예약 생성을 한 묶음으로', async () => {
    await seedUser('alice');
    const db = alice();
    const batch = writeBatch(db);
    batch.delete(doc(db, 'nicknames/alice'));
    batch.update(doc(db, 'users/alice'), { nickname: '새이름', nicknameLower: '새이름' });
    batch.set(doc(db, 'nicknames/새이름'), { uid: 'alice' });
    await assertSucceeds(batch.commit());
  });
  test('같은 닉네임 재제출(예약 쓰기 없이 프로필만 갱신)은 통과한다', async () => {
    await seedUser('alice');
    await assertSucceeds(updateDoc(doc(alice(), 'users/alice'), { nickname: 'alice', nicknameLower: 'alice' }));
  });
  test('새 예약 없이 닉네임만 바꾸거나, 남의 프로필·다른 필드를 바꿀 수 없다', async () => {
    await seedUser('alice');
    await assertFails(updateDoc(doc(alice(), 'users/alice'), { nickname: '새이름', nicknameLower: '새이름' }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { nickname: 'alice', nicknameLower: 'alice' }));
    await assertFails(updateDoc(doc(alice(), 'users/alice'), { color: 1 }));
  });
  test('cellCount 는 누구나 정확히 ±1 만', async () => {
    await seedUser('alice', { cellCount: 5 });
    await assertSucceeds(updateDoc(doc(bob(), 'users/alice'), { cellCount: 4 }));
    await assertSucceeds(updateDoc(doc(bob(), 'users/alice'), { cellCount: 5 }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { cellCount: 7 }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { cellCount: 5, color: 2 }));
    await assertFails(updateDoc(doc(anon(), 'users/alice'), { cellCount: 6 }));
    await seedUser('carol', { cellCount: 0 });
    await assertFails(updateDoc(doc(bob(), 'users/carol'), { cellCount: -1 }));
  });
  test('삭제는 본인만, 닉네임 예약을 함께 지워야 한다', async () => {
    await seedUser('alice');
    await assertFails(deleteDoc(doc(bob(), 'users/alice')));
    await assertFails(deleteDoc(doc(alice(), 'users/alice')));
    const db = alice();
    const batch = writeBatch(db);
    batch.delete(doc(db, 'users/alice'));
    batch.delete(doc(db, 'nicknames/alice'));
    await assertSucceeds(batch.commit());
  });
  test('읽기는 로그인한 사람만', async () => {
    await seedUser('alice');
    await assertSucceeds(getDoc(doc(bob(), 'users/alice')));
    await assertFails(getDoc(doc(anon(), 'users/alice')));
  });
});

describe('nicknames', () => {
  test('이미 있는 예약은 남이 덮어쓸 수 없다(유일성)', async () => {
    await seedUser('alice', { nickname: '땅주인', nicknameLower: '땅주인' });
    await assertFails(signUp(bob(), 'bob'));
  });
  test('본인 프로필이 그 닉네임을 쓰지 않으면 예약할 수 없다(선점 금지)', async () => {
    await assertFails(setDoc(doc(alice(), 'nicknames/땅주인'), { uid: 'alice' }));
    await seedUser('alice');
    await assertFails(setDoc(doc(alice(), 'nicknames/다른이름'), { uid: 'alice' }));
    await assertFails(setDoc(doc(bob(), 'nicknames/다른이름'), { uid: 'alice' }));
  });
  test('본인 예약이라도 update 는 거부', async () => {
    await seedUser('alice');
    await assertFails(setDoc(doc(alice(), 'nicknames/alice'), { uid: 'alice' }));
  });
  test('예약 삭제는 소유자만, 프로필이 아직 그 닉네임을 쓰면 거부', async () => {
    await seedUser('alice');
    await assertFails(deleteDoc(doc(bob(), 'nicknames/alice')));
    await assertFails(deleteDoc(doc(alice(), 'nicknames/alice')));
  });
  test('읽기는 로그인한 사람만', async () => {
    await seedUser('alice');
    await assertSucceeds(getDoc(doc(bob(), 'nicknames/alice')));
    await assertFails(getDoc(doc(anon(), 'nicknames/alice')));
  });
});

describe('cells', () => {
  // 서울시청 res 11 셀과 그 res 8 부모 (h3-js cellToParent 로 확인한 실제 값, 2026-09-29)
  const CELL = '8b30e1d8c0b1fff';
  const REGION = '8830e1d8c1fffff';
  // 10번째 문자가 a~f 인 셀 — res8Parent 의 문자 매핑 하반부를 지난다
  const CELL_HIGH = '8b30e1d8ca0efff';
  const REGION_HIGH = '8830e1d8cbfffff';
  const cell = (uid: string, over: Record<string, unknown> = {}) => ({
    ownerUid: uid, ownerColor: 2, capturedAt: serverTimestamp(), walkedAt: Timestamp.now(), region: REGION, ...over,
  });
  test('본인 소유로 생성·뺏기(update) 가능, 타인 uid·클라 시각·여분 필드·삭제·비로그인은 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), `cells/${CELL}`), cell('alice')));
    await assertSucceeds(setDoc(doc(bob(), `cells/${CELL}`), cell('bob')));
    await assertFails(setDoc(doc(bob(), `cells/${CELL}`), cell('alice')));
    await assertFails(setDoc(doc(bob(), `cells/${CELL}`), cell('bob', { capturedAt: Timestamp.now() })));
    await assertFails(setDoc(doc(bob(), `cells/${CELL}`), cell('bob', { extra: 1 })));
    await assertFails(deleteDoc(doc(bob(), `cells/${CELL}`)));
    await assertFails(setDoc(doc(anon(), `cells/${CELL}`), cell('anon')));
  });
  test('H3 res 11 형식이 아닌 문서 ID 는 거부', async () => {
    await assertFails(setDoc(doc(alice(), 'cells/zz'), cell('alice')));
    await assertFails(setDoc(doc(alice(), 'cells/8B30E1D8C0B1FFF'), cell('alice')));
    await assertFails(setDoc(doc(alice(), 'cells/8a30e1d8c0b7fff'), cell('alice')));
    await assertFails(setDoc(doc(alice(), 'cells/8b30e1d8c0b1ff'), cell('alice')));
    await assertFails(setDoc(doc(alice(), 'cells/8b30e1d8c0b1000'), cell('alice')));
  });
  test('region 은 그 셀의 res 8 부모여야 한다 — res 7 부모·같은 res 7 아래 형제·엉뚱한 값 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), `cells/${CELL_HIGH}`), cell('alice', { region: REGION_HIGH })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: '8730e1d8cffffff' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: '8830e1d8c3fffff' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: '8830e1d8c0fffff' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL_HIGH}`), cell('alice', { region: REGION })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: 'zz' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: 7 })));
  });
  test('ownerColor 는 0..6 정수', async () => {
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { ownerColor: 7 })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { ownerColor: -1 })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { ownerColor: '2' })));
  });
  test('walkedAt 은 필수 timestamp, 5분 넘는 미래는 거부, 기기 시계 오차(1분 앞)·과거는 통과', async () => {
    const withoutWalkedAt: Record<string, unknown> = { ...cell('alice') };
    delete withoutWalkedAt.walkedAt;
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), withoutWalkedAt));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { walkedAt: 1_700_000_000 })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', {
      walkedAt: Timestamp.fromMillis(Date.now() + 10 * 60_000),
    })));
    await assertSucceeds(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', {
      walkedAt: Timestamp.fromMillis(Date.now() + 60_000),
    })));
    await assertSucceeds(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', {
      walkedAt: Timestamp.fromMillis(Date.now() - 6 * 60 * 60 * 1000),
    })));
  });
  test('읽기는 로그인한 사람만', async () => {
    await assertSucceeds(getDoc(doc(bob(), `cells/${CELL}`)));
    await assertFails(getDoc(doc(anon(), `cells/${CELL}`)));
  });
  test('캡처 묶음: 셀 뺏기 + 나 +1 + 이전 소유자 -1 은 통과, +2 는 거부', async () => {
    await seedUser('alice', { cellCount: 3 });
    await seedUser('bob', { cellCount: 0 });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore() as unknown as Firestore, `cells/${CELL}`), {
        ownerUid: 'alice', ownerColor: 1, capturedAt: Timestamp.now(), walkedAt: Timestamp.now(), region: REGION,
      });
    });
    const db = bob();
    const ok = writeBatch(db);
    ok.set(doc(db, `cells/${CELL}`), cell('bob'));
    ok.update(doc(db, 'users/bob'), { cellCount: 1 });
    ok.update(doc(db, 'users/alice'), { cellCount: 2 });
    await assertSucceeds(ok.commit());

    const greedy = writeBatch(db);
    greedy.set(doc(db, `cells/${CELL}`), cell('bob'));
    greedy.update(doc(db, 'users/bob'), { cellCount: 3 });
    await assertFails(greedy.commit());
  });
});

describe('walks', () => {
  const walk = (over: Record<string, unknown> = {}) => ({
    startedAt: Timestamp.fromMillis(Date.now() - 30 * 60_000), endedAt: Timestamp.now(),
    cells: 3, meters: 1235, createdAt: serverTimestamp(), ...over,
  });
  test('본인 아래에 만들 수 있고 본인만 읽는다', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'walks/alice/items/w1'), walk()));
    await assertSucceeds(getDoc(doc(alice(), 'walks/alice/items/w1')));
    await assertFails(getDoc(doc(bob(), 'walks/alice/items/w1')));
  });
  test('타인 아래·비로그인·여분 필드·클라 createdAt 은 거부', async () => {
    await assertFails(setDoc(doc(bob(), 'walks/alice/items/w2'), walk()));
    await assertFails(setDoc(doc(anon(), 'walks/alice/items/w2'), walk()));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w2'), walk({ extra: 1 })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w2'), walk({ createdAt: Timestamp.now() })));
  });
  test('endedAt < startedAt, 5분 넘는 미래, 음수·비정수 칸·미터는 거부', async () => {
    const past = Timestamp.fromMillis(Date.now() - 60 * 60_000);
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ startedAt: Timestamp.now(), endedAt: past })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({
      endedAt: Timestamp.fromMillis(Date.now() + 10 * 60_000),
    })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ cells: -1 })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ meters: 12.5 })));
    await assertSucceeds(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ cells: 0, meters: 0 })));
  });
  test('수정·삭제는 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'walks/alice/items/w4'), walk()));
    await assertFails(updateDoc(doc(alice(), 'walks/alice/items/w4'), { cells: 4 }));
    await assertFails(deleteDoc(doc(alice(), 'walks/alice/items/w4')));
  });
});
