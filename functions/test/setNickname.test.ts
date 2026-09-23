import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInAnonymously } from 'firebase/auth';
import { connectFunctionsEmulator, getFunctions, httpsCallable } from 'firebase/functions';
import { connectFirestoreEmulator, doc, getDoc, getFirestore } from 'firebase/firestore';

const app = initializeApp({ projectId: 'demo-eat-the-land', apiKey: 'fake', appId: 'fake' });
const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
const fns = getFunctions(app, 'asia-northeast3'); connectFunctionsEmulator(fns, '127.0.0.1', 5001);
const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
const setNickname = httpsCallable<{ nickname: string }, { nickname: string; color: number }>(fns, 'setNickname');

async function freshUser() { await auth.signOut(); const c = await signInAnonymously(auth); return c.user.uid; }

test('최초 설정: users·nicknames 문서가 생기고 color 는 0..6', async () => {
  const uid = await freshUser();
  const nick = 'u' + uid.slice(0, 8);
  const res = await setNickname({ nickname: nick });
  expect(res.data.nickname).toBe(nick);
  expect(res.data.color).toBeGreaterThanOrEqual(0);
  expect(res.data.color).toBeLessThan(7);
  const user = await getDoc(doc(db, 'users', uid));
  expect(user.data()).toMatchObject({ nickname: nick, nicknameLower: nick.toLowerCase(), cellCount: 0 });
  expect((await getDoc(doc(db, 'nicknames', nick.toLowerCase()))).data()).toEqual({ uid });
});

test('중복 닉네임은 already-exists (대소문자 무시)', async () => {
  const a = await freshUser();
  const nick = 'Dup' + a.slice(0, 6);
  await setNickname({ nickname: nick });
  await freshUser();
  await expect(setNickname({ nickname: nick.toUpperCase() })).rejects.toMatchObject({ code: 'functions/already-exists' });
});

test('형식 위반은 invalid-argument', async () => {
  await freshUser();
  await expect(setNickname({ nickname: 'a' })).rejects.toMatchObject({ code: 'functions/invalid-argument' });
});

test('변경 시 옛 nicknames 문서는 사라진다', async () => {
  const uid = await freshUser();
  const first = 'f' + uid.slice(0, 8); const second = 's' + uid.slice(0, 8);
  await setNickname({ nickname: first });
  await setNickname({ nickname: second });
  expect((await getDoc(doc(db, 'nicknames', first.toLowerCase()))).exists()).toBe(false);
  expect((await getDoc(doc(db, 'users', uid))).data()?.nickname).toBe(second);
});
