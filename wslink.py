"""Tiny stdlib WebSocket client for the crossover link: python wslink.py [-l secs] 'json' ['json'...]"""
import base64, os, socket, struct, sys, time

def connect(port=25599):
    s = socket.create_connection(("127.0.0.1", port), timeout=5)
    key = base64.b64encode(os.urandom(16)).decode()
    s.sendall(f"GET / HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        buf += s.recv(4096)
    return s, buf.split(b"\r\n\r\n", 1)[1]

def send(s, text):
    data = text.encode()
    mask = os.urandom(4)
    n = len(data)
    head = b"\x81" + (bytes([0x80 | n]) if n < 126 else b"\xfe" + struct.pack(">H", n))
    s.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

def frames(s, rest, secs):
    end = time.time() + secs
    buf = rest
    s.settimeout(0.2)
    while time.time() < end:
        try:
            chunk = s.recv(65536)
            if not chunk:
                break
            buf += chunk
        except socket.timeout:
            pass
        while len(buf) >= 2:
            n = buf[1] & 0x7F
            off = 2
            if n == 126:
                if len(buf) < 4: break
                n = struct.unpack(">H", buf[2:4])[0]; off = 4
            elif n == 127:
                if len(buf) < 10: break
                n = struct.unpack(">Q", buf[2:10])[0]; off = 10
            if len(buf) < off + n: break
            yield buf[off:off + n].decode("utf8", "replace")
            buf = buf[off + n:]

if __name__ == "__main__":
    args = sys.argv[1:]
    secs = 1.0
    if args and args[0] == "-l":
        secs = float(args[1]); args = args[2:]
    s, rest = connect()
    for a in args:
        send(s, a)
    for f in frames(s, rest, secs):
        if '"t":"cam"' in f[:12] or '"t":"ground"' in f[:14]:
            continue
        print(f[:300])
