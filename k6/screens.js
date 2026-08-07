/**
 * 화면 조회 부하.
 *
 * 사용자 한 명이 앱을 여는 흐름을 그대로 흉내 낸다 —
 * 마이 탭을 보고, 기간을 바꿔 보고, 피드로 넘어가고, 미션과 인사이트를 연다.
 * 엔드포인트 하나만 두드리면 실제 화면 전환에서 생기는 부하를 못 본다.
 *
 * 재는 것: 사전 집계가 동시 요청에서도 버티는가. 단일 클라이언트로 잰 0.72ms는
 * 처리량 지표가 아니었다.
 */
import { group, sleep } from 'k6';
import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { BASE_URL, DURATION, VUS } from './config.js';
import { authed, ok, signUp } from './helpers.js';

const overview = new Trend('screen_overview', true);
const peers = new Trend('screen_peers', true);
const feed = new Trend('screen_feed', true);
const missions = new Trend('screen_missions', true);
const projection = new Trend('screen_projection', true);

export const options = {
    scenarios: {
        screens: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        // 화면 전환이 느리면 앱이 멈춘 것처럼 보인다
        http_req_failed: ['rate==0'],
        screen_overview: ['p(95)<500'],
        screen_peers: ['p(95)<500'],
    },
};

export function setup() {
    // VU마다 가입하면 부하 구간에 가입 비용이 섞인다. 미리 만들어 나눠 준다.
    const tokens = [];
    for (let i = 0; i < VUS; i++) {
        const t = signUp(`load-${i}`);
        if (t) tokens.push(t);
    }
    if (tokens.length === 0) {
        throw new Error('토큰을 하나도 만들지 못했습니다. 서버와 데이터를 확인하세요.');
    }
    return { tokens };
}

export default function (data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    const h = authed(token);

    group('마이', () => {
        for (const period of ['daily', 'weekly', 'monthly']) {
            const res = http.get(`${BASE_URL}/api/v1/me/overview?period=${period}`, h);
            ok(res, `overview ${period}`);
            overview.add(res.timings.duration);
        }
    });

    group('피드', () => {
        let res = http.get(`${BASE_URL}/api/v1/me/peers`, h);
        ok(res, 'peers');
        peers.add(res.timings.duration);

        res = http.get(`${BASE_URL}/api/v1/me/feed/groups`, h);
        ok(res, 'groups');
        feed.add(res.timings.duration);

        res = http.get(`${BASE_URL}/api/v1/me/feed/mates?limit=12`, h);
        ok(res, 'mates');
        feed.add(res.timings.duration);
    });

    group('미션', () => {
        const res = http.get(`${BASE_URL}/api/v1/me/missions`, h);
        ok(res, 'missions');
        missions.add(res.timings.duration);
    });

    group('인사이트', () => {
        const res = http.get(`${BASE_URL}/api/v1/me/projection`, h);
        ok(res, 'projection');
        projection.add(res.timings.duration);
    });

    sleep(0.5);
}
