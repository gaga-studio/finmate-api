import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';

/**
 * 가입해서 토큰을 받는다.
 *
 * 가입할 때 합성 인구 한 명이 배정되므로, VU마다 서로 다른 원장을 보게 된다.
 * 같은 계정을 나눠 쓰면 캐시가 없는데도 있는 것처럼 빨라 보인다.
 */
export function signUp(label) {
    const suffix = `${label}-${__VU}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
    const res = http.post(`${BASE_URL}/api/v1/auth/signup`, JSON.stringify({
        email: `${suffix}@example.com`,
        password: 'a-long-enough-password',
        displayName: 'k6',
    }), { headers: { 'Content-Type': 'application/json' } });

    if (res.status !== 201) {
        return null;
    }
    return res.json('accessToken');
}

export function authed(token) {
    return { headers: { Authorization: `Bearer ${token}` }, tags: {} };
}

export function ok(res, name) {
    return check(res, { [`${name} 200`]: (r) => r.status === 200 });
}
