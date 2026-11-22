#!/usr/bin/env python3
"""
Fake GPS devices for demos and load checks. Standard library only (Python 3.8+).

  python3 simulate_vehicles.py --token "<JWT>" --vehicles 1,2,3
  python3 simulate_vehicles.py --token "<JWT>" --vehicles 1-50 --interval 5 --batch 3 --duration 120

Each vehicle drives a smooth random route around Bengaluru and posts a batch of pings every --interval seconds
to POST /api/tracking/positions. Press Ctrl+C to stop.
"""
import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

CENTER = (12.9716, 77.5946)  # Bengaluru
RADIUS_DEG = 0.12            # roam about 13 km around the centre


def parse_ids(text):
    ids = []
    for part in text.split(","):
        part = part.strip()
        if "-" in part:
            a, b = part.split("-", 1)
            ids.extend(range(int(a), int(b) + 1))
        elif part:
            ids.append(int(part))
    return ids


def iso(dt):
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") + f"{dt.microsecond // 1000:03d}Z"


class Vehicle:
    def __init__(self, vehicle_id):
        self.id = vehicle_id
        self.lat = CENTER[0] + random.uniform(-RADIUS_DEG, RADIUS_DEG) / 2
        self.lng = CENTER[1] + random.uniform(-RADIUS_DEG, RADIUS_DEG) / 2
        self.heading = random.uniform(0, 360)
        self.speed = random.uniform(15, 45)

    def step(self, seconds):
        self.heading = (self.heading + random.uniform(-20, 20)) % 360
        self.speed = min(60.0, max(8.0, self.speed + random.uniform(-4, 4)))
        # turn back towards the centre when drifting too far away
        if abs(self.lat - CENTER[0]) > RADIUS_DEG or abs(self.lng - CENTER[1]) > RADIUS_DEG:
            to_centre = math.degrees(math.atan2(CENTER[1] - self.lng, CENTER[0] - self.lat)) % 360
            self.heading = to_centre
        km = self.speed * seconds / 3600.0
        rad = math.radians(self.heading)
        self.lat += km * math.cos(rad) / 111.0
        self.lng += km * math.sin(rad) / (111.0 * math.cos(math.radians(self.lat)))

    def ping(self, at):
        return {
            "lat": round(self.lat, 6),
            "lng": round(self.lng, 6),
            "speedKmh": round(self.speed, 1),
            "heading": round(self.heading, 1),
            "recordedAt": iso(at),
        }


def post(base, token, vehicle_id, pings):
    body = json.dumps({"vehicleId": vehicle_id, "positions": pings}).encode()
    req = urllib.request.Request(
        f"{base}/api/tracking/positions",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except urllib.error.URLError as e:
        return 0, str(e.reason)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--token", required=True, help="JWT sent as Bearer token")
    ap.add_argument("--vehicles", required=True, help="vehicle ids, e.g. 1,2,3 or 1-50")
    ap.add_argument("--interval", type=float, default=5.0, help="seconds between requests per vehicle")
    ap.add_argument("--batch", type=int, default=1, help="pings per request (simulates buffering)")
    ap.add_argument("--duration", type=float, default=0, help="stop after N seconds (0 = run until Ctrl+C)")
    args = ap.parse_args()

    if not 1 <= args.batch <= 500:
        sys.exit("--batch must be between 1 and 500")
    vehicles = [Vehicle(i) for i in parse_ids(args.vehicles)]
    spacing = args.interval / args.batch
    started = time.time()
    sent = errors = 0
    print(f"Simulating {len(vehicles)} vehicle(s), {args.batch} ping(s) every {args.interval}s -> {args.base}")

    try:
        while True:
            round_start = time.time()
            now = datetime.now(timezone.utc)
            for v in vehicles:
                pings = []
                for i in range(args.batch):
                    v.step(spacing)
                    pings.append(v.ping(now - timedelta(seconds=spacing * (args.batch - 1 - i))))
                status, text = post(args.base, args.token, v.id, pings)
                if status == 202:
                    sent += len(pings)
                else:
                    errors += 1
                    print(f"vehicle {v.id}: HTTP {status} {text[:200]}")
            print(f"[{time.strftime('%H:%M:%S')}] pings sent: {sent}  failed requests: {errors}")
            if args.duration and time.time() - started >= args.duration:
                break
            time.sleep(max(0.0, args.interval - (time.time() - round_start)))
    except KeyboardInterrupt:
        print("\nstopped")


if __name__ == "__main__":
    main()
