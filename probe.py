import json, sys, time
sys.path.insert(0, '.')
import wslink
s, rest = wslink.connect()
def probe(x, y, z, h, n, step):
    wslink.send(s, json.dumps({"t":"gta","op":"probe","x":x,"y":y,"z":z,"h":h,"step":step,"n":n}))
    for f in wslink.frames(s, b"", 2.0):
        if '"probe"' in f:
            return json.loads(f)["probe"]
    return None
if __name__ == "__main__":
    x, y = float(sys.argv[1]), float(sys.argv[2])
    for h in range(0, 360, 20):
        r = probe(x, y, 150, h, 40, 6)
        print(h, None if r is None else " ".join(f"{v:.0f}" for v in r), flush=True)
