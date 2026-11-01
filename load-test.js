import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 100 },
        { duration: '20s', target: 500 },
        { duration: '30s', target: 1000 },
        { duration: '30s', target: 1000 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'], // Expect less than 1% failure rate
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    // 1. Ensure this URL matches your controller mapping (e.g., http://localhost:8080/inventory)
    const url = 'http://localhost:8080/warehouse';

    // 2. Pass a freshly generated, valid JWT token here
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJpbnZlbnRvcnktbWFuYWdlbWVudCIsInN1YiI6IjEiLCJleHAiOjE3OTI5MTAxNTYsImlhdCI6MTc5MjkwNjU1NiwiZW1haWwiOiJuaW1pc2hAZ21haWwuY29tIiwicm9sZXMiOlsiUk9MRV9VU0VSIl19.V7zNjsrQDM1RE3_xMDuxwhUQjIMVRic_F4iH5br0vvE', // Replace with a fresh token
        },
    };

    const res = http.get(url, params);

    // 3. Debug any failures directly in console if they occur
    if (res.status !== 200) {
        console.log(`Failed with status ${res.status}: ${res.body}`);
    }

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(1);
}