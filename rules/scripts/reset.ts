import { cert, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { resolve } from 'path';

// 개발용: cells 를 전부 지우고 모든 users.cellCount 를 0 으로 되돌린다(스펙 §4 개발 스크립트).
// region 해상도·문서 형식이 바뀌어 옛 문서가 규칙을 통과하지 못할 때 쓴다. 서비스 계정 키는 rules/serviceAccount.json (커밋 금지).
initializeApp({ credential: cert(resolve(__dirname, '../serviceAccount.json')) });
const db = getFirestore();
(async () => {
  const cells = await db.collection('cells').listDocuments();
  for (let i = 0; i < cells.length; i += 400) {
    const batch = db.batch();
    cells.slice(i, i + 400).forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
  const users = await db.collection('users').listDocuments();
  for (let i = 0; i < users.length; i += 400) {
    const batch = db.batch();
    users.slice(i, i + 400).forEach((ref) => batch.update(ref, { cellCount: 0 }));
    await batch.commit();
  }
  console.log(`deleted ${cells.length} cells, reset ${users.length} users`);
})();
