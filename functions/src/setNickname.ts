import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { COLOR_COUNT, isValidNickname } from './nickname';

export const setNickname = onCall({ region: 'asia-northeast3' }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', '로그인이 필요합니다');
  const nickname = req.data?.nickname;
  if (!isValidNickname(nickname)) throw new HttpsError('invalid-argument', '닉네임은 한글·영문·숫자 2~12자');
  const lower = nickname.toLowerCase();
  const db = getFirestore();

  return db.runTransaction(async (tx) => {
    const nickRef = db.doc(`nicknames/${lower}`);
    const userRef = db.doc(`users/${uid}`);
    const counterRef = db.doc(`meta/counters`);
    const [nickSnap, userSnap, counterSnap] = await Promise.all([tx.get(nickRef), tx.get(userRef), tx.get(counterRef)]);

    if (nickSnap.exists && nickSnap.data()?.uid !== uid) throw new HttpsError('already-exists', '이미 사용 중인 닉네임');

    let color: number;
    if (userSnap.exists) {
      const user = userSnap.data()!;
      color = user.color as number;
      const oldLower = user.nicknameLower as string;
      if (oldLower !== lower) tx.delete(db.doc(`nicknames/${oldLower}`));
      tx.update(userRef, { nickname, nicknameLower: lower });
    } else {
      const seq = (counterSnap.data()?.users as number | undefined) ?? 0;
      color = seq % COLOR_COUNT;
      tx.set(counterRef, { users: seq + 1 }, { merge: true });
      tx.set(userRef, { nickname, nicknameLower: lower, color, cellCount: 0, createdAt: FieldValue.serverTimestamp() });
    }
    tx.set(nickRef, { uid });
    return { nickname, color };
  });
});
