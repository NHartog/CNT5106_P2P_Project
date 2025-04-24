#!/bin/bash

PROJECT_DIR="/Users/sebastianpaulis/IdeaProjects/CNT5106_P2P_Project/"  # :arrow_left: CHANGE THIS

for i in {1005..1008}
do
  osascript <<EOF
tell application "Terminal"
    do script "cd \"$PROJECT_DIR\" && java PeerProcess $i"
end tell
EOF
done
