#!/bin/sh
# Start a virtual X11 display so Firefox can run non-headlessly (bypasses Cloudflare headless detection)
Xvfb :99 -screen 0 1920x1080x24 -ac +extension GLX +render -nolisten tcp &
export DISPLAY=:99
sleep 1
exec uvicorn main:app --host 0.0.0.0 --port 8000
