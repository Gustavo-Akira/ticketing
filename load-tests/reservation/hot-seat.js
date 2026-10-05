import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        hot_seat: {
            executor: 'per-vu-iterations',
            vus: 100,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID?.trim();
const SEAT_ID = __ENV.SEAT_ID?.trim();
const TOKEN = __ENV.TOKEN?.trim()
export default function () {
    const response = http.post(
        `${BASE_URL}/v1/reservations`,
        JSON.stringify({
            eventId: EVENT_ID,
            seatIds: [SEAT_ID],
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${TOKEN}`,
            },
        }
    );
    if (__VU <= 5) {
        console.log(
            JSON.stringify({
                vu: __VU,
                status: response.status,
                body: response.body,
                url: response.url
            })
        );
    }
    check(response, {
        'Expected status': (r) =>
            r.status === 201 || r.status === 409,
    });
}