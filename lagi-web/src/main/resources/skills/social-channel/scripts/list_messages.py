#!/usr/bin/env python3
"""List latest messages of a social channel via LinkMind HTTP API.

Usage:
    python list_messages.py --user-id <USER_ID> --channel-id <CHANNEL_ID> \
        [--limit 20] [--before-id <BEFORE_ID>] \
        [--start-time "YYYY-MM-DD HH:MM:SS"] [--end-time "YYYY-MM-DD HH:MM:SS"] \
        [--base-url http://localhost:18080]

Only Python standard library is used (json, argparse, urllib).
"""

import argparse
import sys
import urllib.error
import urllib.parse
import urllib.request


def main() -> int:
    parser = argparse.ArgumentParser(description="List social channel messages")
    parser.add_argument("--user-id", required=True)
    parser.add_argument("--channel-id", required=True, type=int)
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--before-id", type=int, default=None)
    parser.add_argument("--start-time", default=None,
                        help='Inclusive lower bound of created_at, format "YYYY-MM-DD HH:MM:SS".')
    parser.add_argument("--end-time", default=None,
                        help='Inclusive upper bound of created_at, format "YYYY-MM-DD HH:MM:SS".')
    parser.add_argument("--base-url", default="http://localhost:18080")
    parser.add_argument("--timeout", type=int, default=15)
    args = parser.parse_args()

    params = {
        "userId": args.user_id,
        "channelId": args.channel_id,
        "limit": args.limit,
    }
    if args.before_id is not None:
        params["beforeId"] = args.before_id
    if args.start_time:
        params["startTime"] = args.start_time
    if args.end_time:
        params["endTime"] = args.end_time

    url = args.base_url.rstrip("/") + "/socialChannel/listMessages?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=args.timeout) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
    except Exception as e:
        print('{"status":"failed","msg":"' + str(e).replace('"', "'") + '"}')
        return 1
    print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
