"""cutclip.py <take> <out.mp4> [length]: muxes a take (mkv + audio) and cuts it from the frame where the title appears."""
import subprocess, sys, numpy as np
take, out = sys.argv[1], sys.argv[2]
length = float(sys.argv[3]) if len(sys.argv) > 3 else 20.0
mux = f"gen/{take}_full.mp4"
subprocess.run(["um", "video", "mux", f"gen/{take}.mkv", f"gen/{take}.audio.raw", f"gen/{take}.json", mux], check=True, capture_output=True)
W, H, FPS = 480, 270, 30
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", mux, "-t", "16", "-vf", f"fps={FPS},scale={W}:{H},format=gray", "-f", "rawvideo", "-"],
                     capture_output=True).stdout
frames = np.frombuffer(raw, np.uint8).reshape(-1, H, W).astype(np.int16)
region = (slice(int(H * .30), int(H * .50)), slice(int(W * .10), int(W * .33)))  # left of Steve, where 'ELYTRA' appears
base = frames[0][region]
start = None
for i, f in enumerate(frames):
    if np.abs(f[region] - base).mean() > 6:   # the title fades in over the static roof
        start = i / FPS
        break
if start is None:
    sys.exit("title not found")
start += 0.15   # title mostly opaque
print("title at", round(start, 2), "s")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{start:.2f}", "-i", mux, "-t", str(length), "-c:v", "libx264", "-preset", "slow", "-crf", "17",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,afade=t=out:st=%.2f:d=0.8" % (length - 0.8), "-movflags", "+faststart", out], check=True)
print("wrote", out)
