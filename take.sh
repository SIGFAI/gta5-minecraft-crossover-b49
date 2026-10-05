#!/bin/bash
# take.sh <name> <seconds>: records GTA's window, starts the demo once frames are flowing, writes a contact sheet gen/<name>_sheet.png
N=$1; S=${2:-26}
rm -f gen/$N.mkv
(um win record --exe GTA5.exe --out gen/$N --seconds $S --fps 30 > gen/rec.log 2>&1 &)
for i in $(seq 1 60); do
  sleep 0.5
  if [ -f gen/$N.mkv ] && [ $(stat -c %s gen/$N.mkv) -gt 300000 ]; then break; fi
done
echo -n 1 > C:/mod/work/xo-rec.txt
sleep $((S+4))
ffmpeg -v error -y -i gen/$N.mkv -vf "fps=1/2,scale=480:-1,tile=4x4" -frames:v 1 gen/${N}_sheet.png
