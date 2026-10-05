
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const created = new Counter('reservations_created');
const conflicts = new Counter('reservation_conflicts');
const unexpected = new Counter('reservation_unexpected');

const groupAWins = new Counter('group_a_wins');
const groupBWins = new Counter('group_b_wins');

http.setResponseCallback(
    http.expectedStatuses(201, 409)
);

export const options = {
    scenarios: {
        partial_overlap: {
            executor: 'per-vu-iterations',
            vus: 100,
            iterations: 1,
            maxDuration: '30s',
        },
    },

    thresholds: {
        reservations_created: ['count==1'],
        reservation_conflicts: ['count==99'],
        reservation_unexpected: ['count==0'],
    },
};

const BASE_URL = (
    __ENV.BASE_URL || 'http://localhost:8080'
).trim().replace(/\/$/, '');

const EVENT_ID = __ENV.EVENT_ID?.trim();
const TOKEN = __ENV.TOKEN?.trim();

const SEAT_IDS = (__ENV.SEAT_IDS || '')
    .split(',')
    .map(id => id.trim())
    .filter(Boolean);

if (!EVENT_ID || !TOKEN) {
    throw new Error('EVENT_ID and TOKEN are required');
}

if (SEAT_IDS.length !== 3 ||
    new Set(SEAT_IDS).size !== 3) {
    throw new Error('Provide exactly 3 unique seat IDs');
}

export default function () {

    const group = __VU % 2 === 0 ? 'A' : 'B';

    const selectedSeats = group === 'A'
        ? [SEAT_IDS[0], SEAT_IDS[1]]
        : [SEAT_IDS[1], SEAT_IDS[2]];

    const response = http.post(
        `${BASE_URL}/v1/reservations`,
        JSON.stringify({
            eventId: EVENT_ID,
            seatIds: selectedSeats,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${TOKEN}`,
            },
        }
    );

    switch (response.status) {
        case 201:
            created.add(1);

            if (group === 'A') {
                groupAWins.add(1);
            } else {
                groupBWins.add(1);
            }

            console.log(
                `WINNER: Group ${group}, VU ${__VU}`
            );
            break;

        case 409:
            conflicts.add(1);
            break;

        default:
            unexpected.add(1);

            console.error(JSON.stringify({
                vu: __VU,
                group,
                status: response.status,
                body: response.body,
            }));
    }

    check(response, {
        'Expected response': r =>
            r.status === 201 || r.status === 409,
    });
}
