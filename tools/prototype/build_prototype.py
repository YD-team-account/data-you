"""인터랙티브 프로토타입 빌드: template.html + timeline.json → index.html (단일 파일)

사용법 (프로젝트 루트):
  ./gradlew :core-engine:run --args="timeline docs/prototype/timeline.json"
  python3 tools/prototype/build_prototype.py
"""
from pathlib import Path

root = Path(__file__).resolve().parent.parent.parent
proto = root / "docs" / "prototype"
template = (proto / "template.html").read_text(encoding="utf-8")
timeline = (proto / "timeline.json").read_text(encoding="utf-8")
# </script> 가 JSON 안에 들어가도 스크립트 태그가 끊기지 않도록
timeline = timeline.replace("</", "<\\/")
import json, sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import routes
data = json.loads((proto / "timeline.json").read_text(encoding="utf-8"))
route_map = {}
for key, runs in data["scenarios"].items():
    frames = next(iter(runs.values()))["frames"]
    route_map[key] = routes.build(root / "sample-data" / f"{key}.csv", [f[0] for f in frames])
out = template.replace("__TIMELINE__", timeline).replace("__ROUTES__", json.dumps(route_map, separators=(",", ":")))
(proto / "index.html").write_text(out, encoding="utf-8")
print(f"wrote {proto / 'index.html'} ({len(out) // 1024}KB)")
