import { cert, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { cellToParent, latLngToCell } from 'h3-js';
import { resolve } from 'path';

// 개발용: 지정 좌표 주변에 다른 유저 소유 셀 3개를 심는다(region 은 res 8 — 스펙 §2 v3). 서비스 계정 키는 rules/serviceAccount.json (커밋 금지).
initializeApp({ credential: cert(resolve(__dirname, '../serviceAccount.json')) });
const [lat, lng] = process.argv.slice(2).map(Number);
if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
  console.error('usage: npm run seed -- <lat> <lng>');
  process.exit(1);
}
const db = getFirestore();
(async () => {
  const offsets = [[0, 0], [0.0006, 0], [0, 0.0007]];
  for (const [i, [dl, dg]] of offsets.entries()) {
    const cell = latLngToCell(lat + dl, lng + dg, 11);
    await db.doc(`cells/${cell}`).set({
      ownerUid: `seed-${i}`, ownerColor: i + 1, capturedAt: Timestamp.now(), walkedAt: Timestamp.now(),
      region: cellToParent(cell, 8),
    });
    console.log('seeded', cell);
  }
})();
