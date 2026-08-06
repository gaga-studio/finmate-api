/**
 * 그림일기 동시 요청 — 하루에 정말 한 장인가.
 *
 * 통합 테스트에서 스레드 8개로 확인했지만, 그건 한 JVM 안이다.
 * 여기서는 HTTP로 진짜 동시에 때린다. 202(새로 만듦)가 딱 한 번만 나와야 한다.
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL } from './config.js';
import { authed, signUp } from './helpers.js';

const created = new Counter('diary_created');
const existing = new Counter('diary_existing');

export const options = {
    scenarios: {
        burst: {
            executor: 'per-vu-iterations',
            vus: 20,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        // 20명이 동시에 요청해도 새로 만들어지는 건 하나뿐이어야 한다
        diary_created: ['count==1'],
    },
};

export function setup() {
    const token = signUp('diary');
    if (!token) throw new Error('가입에 실패했습니다');
    const h = authed(token);
    const overview = http.get(`${BASE_URL}/api/v1/me/overview?period=monthly`, h);
    return { token, date: overview.json('referenceDate') };
}

export default function (data) {
    const res = http.post(`${BASE_URL}/api/v1/me/diary/${data.date}`, null, authed(data.token));
    check(res, { '202 또는 200': (r) => r.status === 202 || r.status === 200 });
    if (res.status === 202) created.add(1);
    if (res.status === 200) existing.add(1);
}
