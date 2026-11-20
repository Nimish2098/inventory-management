import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const options = {
    stages: [
        { duration: '10s', target: 100 },
        { duration: '20s', target: 500 },
        { duration: '30s', target: 1000 },
        { duration: '30s', target: 1000 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export function setup() {
    const email = __ENV.LOAD_TEST_EMAIL;
    const password = __ENV.LOAD_TEST_PASSWORD;

    if (!email || !password) {
        throw new Error('Set LOAD_TEST_EMAIL and LOAD_TEST_PASSWORD for a dedicated test account');
    }

    const loginResponse = http.post(
        `${baseUrl}/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (loginResponse.status !== 200) {
        throw new Error(`Load-test login failed with HTTP ${loginResponse.status}`);
    }

    const accessToken = loginResponse.json('accessToken');
    if (typeof accessToken !== 'string' || accessToken.length === 0) {
        throw new Error('Load-test login response did not contain an access token');
    }

    return { accessToken };
}

export default function ({ accessToken }) {
    const response = http.get(`${baseUrl}/warehouse`, {
        headers: {
            Authorization: `Bearer ${accessToken}`,
        },
    });

    if (response.status !== 200) {
        console.log(`Warehouse request failed with HTTP ${response.status}`);
    }

    check(response, {
        'warehouse request status is 200': (result) => result.status === 200,
    });

    sleep(1);
}
