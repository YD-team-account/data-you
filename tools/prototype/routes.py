"""녹화 주행 CSV(속도·요레이트)로 실제 지나간 길을 복원한다 (앱 RouteGeometry와 같은 방식의 추측 항법).

프로토타입 지도용으로 시나리오마다 다음을 만든다.
  pose  : 타임라인 프레임(1초)마다 [x, y, 방위(도, x축 기준 반시계), 누적거리 m]
  road  : 경로 polyline (약 4m 간격, SVG 좌표: x 동쪽, y 남쪽)
  side  : 경로 옆 골목 (직선 구간에 일정 간격, 지도처럼 보이게)
  turns : 회전 지점 [누적거리, 방향(+1 좌 / -1 우)]
"""
import csv, math


def _wrap(a):
    while a > math.pi: a -= 2 * math.pi
    while a < -math.pi: a += 2 * math.pi
    return a


def build(csv_path, frame_times_s):
    rows = list(csv.DictReader(open(csv_path, encoding="utf-8")))
    ts, xs, ys, hs, ss = [], [], [], [], []
    x = y = s = 0.0
    h = math.pi / 2  # 시작은 북쪽(화면 위)
    prev_t = None
    h0 = None
    for r in rows:
        t = int(r["timestamp_ms"]) / 1000.0
        v = float(r["speed_mps"])
        yaw = r.get("yaw_rate_rad_s", "")
        hd = r.get("heading_deg", "")
        if prev_t is not None:
            dt = t - prev_t
            if yaw not in ("", None):
                h += float(yaw) * dt
            elif hd not in ("", None):
                if h0 is None: h0 = float(hd)
                h = math.pi / 2 - math.radians(float(hd) - h0)
            x += v * dt * math.cos(h)
            y += v * dt * math.sin(h)
            s += v * dt
        prev_t = t
        ts.append(t); xs.append(x); ys.append(y); hs.append(h); ss.append(s)

    # 프레임마다 위치
    pose, j = [], 0
    for ft in frame_times_s:
        while j < len(ts) - 1 and ts[j + 1] <= ft: j += 1
        pose.append([round(xs[j], 1), round(ys[j], 1), round(math.degrees(hs[j]), 1), round(ss[j], 1)])

    # 약 4m 간격 polyline
    pts, last = [], -1e9
    for i in range(len(ts)):
        if ss[i] - last >= 4 or i == len(ts) - 1:
            pts.append((xs[i], ys[i], hs[i], ss[i])); last = ss[i]
    road = "M" + " L".join(f"{p[0]:.1f} {-p[1]:.1f}" for p in pts) if pts else ""

    # 회전 지점: 40m 안에서 방위가 55° 넘게 바뀌는 곳 (간격 120m 이상)
    turns, k = [], 0
    for i in range(len(pts)):
        while k < len(pts) - 1 and pts[k][3] - pts[i][3] < 40: k += 1
        d = _wrap(pts[k][2] - pts[i][2])
        if abs(d) > math.radians(55) and (not turns or pts[i][3] - turns[-1][0] > 120):
            turns.append([round(pts[i][3] + 20, 1), 1 if d > 0 else -1])

    # 골목: 직선 구간에서 90m마다 양옆으로
    side, nxt = [], 60.0
    for i in range(1, len(pts) - 1):
        px, py, ph, ps = pts[i]
        if ps < nxt: continue
        if abs(_wrap(pts[i + 1][2] - pts[i - 1][2])) > 0.05: continue
        nxt = ps + 90
        nx, ny = -math.sin(ph), math.cos(ph)
        for sign, ln in ((1, 260), (-1, 200)):
            if int(ps / 90) % 3 == (0 if sign > 0 else 1): continue
            ax, ay = px + nx * 22 * sign, py + ny * 22 * sign
            bx, by = px + nx * ln * sign, py + ny * ln * sign
            side.append(f"M{ax:.0f} {-ay:.0f} L{bx:.0f} {-by:.0f}")
    return {"pose": pose, "road": road, "side": " ".join(side), "turns": turns, "len": round(s)}
