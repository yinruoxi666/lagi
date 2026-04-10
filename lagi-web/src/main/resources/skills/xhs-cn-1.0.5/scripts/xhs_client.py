#!/usr/bin/env python3
"""
Xiaohongshu MCP Client - Python client for xiaohongshu-mcp HTTP API.

Usage:
    python xhs_client.py <command> [options]

Commands:
    status              Check login status
    search <keyword>    Search notes by keyword
    detail <feed_id> <xsec_token>   Get note details
    feeds               Get recommended feed list
    publish <title> <content> <images>  Publish a note
"""

import argparse
import json
import sys

try:
    import requests
except ImportError:
    print("❌ Missing dependency: requests. Install with: pip install requests")
    sys.exit(1)

BASE_URL = "http://localhost:18060"

# Most API calls complete within 30s; publish may take longer due to image upload
DEFAULT_TIMEOUT = 30
PUBLISH_TIMEOUT = 120

CONNECTION_ERROR_MSG = (
    "Cannot connect to MCP server at {url}. "
    "Ensure xiaohongshu-mcp is running. See SETUP.md for instructions."
)


def _request(method, path, **kwargs):
    """Unified request handler with consistent error handling.

    Returns parsed JSON on success, or an error dict on failure.
    Never exits the process — callers decide how to handle errors.
    """
    kwargs.setdefault("timeout", DEFAULT_TIMEOUT)
    url = f"{BASE_URL}{path}"
    try:
        resp = requests.request(method, url, **kwargs)
        resp.raise_for_status()
        return resp.json()
    except requests.exceptions.ConnectionError:
        return {"success": False, "error": CONNECTION_ERROR_MSG.format(url=BASE_URL)}
    except requests.exceptions.Timeout:
        return {"success": False, "error": f"Request timed out after {kwargs['timeout']}s"}
    except requests.exceptions.HTTPError as e:
        return {"success": False, "error": f"HTTP {e.response.status_code}: {e.response.text[:200]}"}
    except (ValueError, json.JSONDecodeError):
        return {"success": False, "error": "Invalid JSON response from server"}


def check_status():
    """Check login status."""
    data = _request("GET", "/api/v1/login/status")
    if data.get("success"):
        login_info = data.get("data", {})
        if login_info.get("is_logged_in"):
            print(f"✅ Logged in as: {login_info.get('username', 'Unknown')}")
        else:
            print("❌ Not logged in. Run the login tool first (see SETUP.md).")
    else:
        print(f"❌ Error: {data.get('error', 'Unknown error')}")
    return data


def search_notes(keyword, sort_by="综合", note_type="不限", publish_time="不限"):
    """Search notes by keyword with optional filters."""
    payload = {
        "keyword": keyword,
        "filters": {
            "sort_by": sort_by,
            "note_type": note_type,
            "publish_time": publish_time
        }
    }
    data = _request("POST", "/api/v1/feeds/search", json=payload)

    if data.get("success"):
        feeds = data.get("data", {}).get("feeds", [])
        print(f"🔍 Found {len(feeds)} notes for '{keyword}':\n")

        for i, feed in enumerate(feeds, 1):
            note_card = feed.get("noteCard", {})
            user = note_card.get("user", {})
            interact = note_card.get("interactInfo", {})

            print(f"[{i}] {note_card.get('displayTitle', 'No title')}")
            print(f"    Author: {user.get('nickname', 'Unknown')}")
            print(f"    Likes: {interact.get('likedCount', '0')} | "
                  f"Collects: {interact.get('collectedCount', '0')} | "
                  f"Comments: {interact.get('commentCount', '0')}")
            print(f"    feed_id: {feed.get('id')}")
            print(f"    xsec_token: {feed.get('xsecToken')}")
            print()
    else:
        print(f"❌ Search failed: {data.get('error', 'Unknown error')}")

    return data


def get_note_detail(feed_id, xsec_token, load_comments=False):
    """Get detailed information about a specific note."""
    payload = {
        "feed_id": feed_id,
        "xsec_token": xsec_token,
        "load_all_comments": load_comments
    }
    data = _request("POST", "/api/v1/feeds/detail", json=payload)

    if data.get("success"):
        note_data = data.get("data", {}).get("data", {})
        note = note_data.get("note", {})
        comments = note_data.get("comments", {})

        print("📝 Note Details:\n")
        print(f"Title: {note.get('title', 'No title')}")
        print(f"Author: {note.get('user', {}).get('nickname', 'Unknown')}")
        print(f"Location: {note.get('ipLocation', 'Unknown')}")
        print(f"\nContent:\n{note.get('desc', 'No content')}\n")

        interact = note.get("interactInfo", {})
        print(f"Likes: {interact.get('likedCount', '0')} | "
              f"Collects: {interact.get('collectedCount', '0')} | "
              f"Comments: {interact.get('commentCount', '0')}")

        comment_list = comments.get("list", [])
        if comment_list:
            print(f"\n💬 Top Comments ({len(comment_list)}):")
            for c in comment_list[:5]:
                user_info = c.get("userInfo", {})
                print(f"  - {user_info.get('nickname', 'Anonymous')}: {c.get('content', '')}")
    else:
        print(f"❌ Failed to get details: {data.get('error', 'Unknown error')}")

    return data


def get_feeds():
    """Get recommended feed list."""
    data = _request("GET", "/api/v1/feeds/list")

    if data.get("success"):
        feeds = data.get("data", {}).get("feeds", [])
        print(f"📋 Recommended Feeds ({len(feeds)} notes):\n")

        for i, feed in enumerate(feeds, 1):
            note_card = feed.get("noteCard", {})
            user = note_card.get("user", {})
            interact = note_card.get("interactInfo", {})

            print(f"[{i}] {note_card.get('displayTitle', 'No title')}")
            print(f"    Author: {user.get('nickname', 'Unknown')}")
            print(f"    Likes: {interact.get('likedCount', '0')}")
            print()
    else:
        print(f"❌ Failed to get feeds: {data.get('error', 'Unknown error')}")

    return data


def publish_note(title, content, images, tags=None):
    """Publish a new note."""
    payload = {
        "title": title,
        "content": content,
        "images": images if isinstance(images, list) else [images]
    }
    if tags:
        payload["tags"] = tags if isinstance(tags, list) else [tags]

    data = _request("POST", "/api/v1/publish", json=payload, timeout=PUBLISH_TIMEOUT)

    if data.get("success"):
        print("✅ Note published successfully!")
        print(f"   Post ID: {data.get('data', {}).get('post_id', 'Unknown')}")
    else:
        print(f"❌ Publish failed: {data.get('error', 'Unknown error')}")

    return data


def main():
    parser = argparse.ArgumentParser(
        description="Xiaohongshu MCP Client",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # status
    status_parser = subparsers.add_parser("status", help="Check login status")
    status_parser.add_argument("--json", action="store_true", help="Output raw JSON")

    # search
    search_parser = subparsers.add_parser("search", help="Search notes")
    search_parser.add_argument("keyword", help="Search keyword")
    search_parser.add_argument("--sort", default="综合",
                               choices=["综合", "最新", "最多点赞", "最多评论", "最多收藏"],
                               help="Sort by")
    search_parser.add_argument("--type", default="不限",
                               choices=["不限", "视频", "图文"],
                               help="Note type")
    search_parser.add_argument("--time", default="不限",
                               choices=["不限", "一天内", "一周内", "半年内"],
                               help="Publish time filter")
    search_parser.add_argument("--json", action="store_true", help="Output raw JSON")

    # detail
    detail_parser = subparsers.add_parser("detail", help="Get note details")
    detail_parser.add_argument("feed_id", help="Feed ID from search results")
    detail_parser.add_argument("xsec_token", help="Security token from search results")
    detail_parser.add_argument("--comments", action="store_true", help="Load all comments")
    detail_parser.add_argument("--json", action="store_true", help="Output raw JSON")

    # feeds
    feeds_parser = subparsers.add_parser("feeds", help="Get recommended feeds")
    feeds_parser.add_argument("--json", action="store_true", help="Output raw JSON")

    # publish
    publish_parser = subparsers.add_parser("publish", help="Publish a note")
    publish_parser.add_argument("title", help="Note title")
    publish_parser.add_argument("content", help="Note content")
    publish_parser.add_argument("images", help="Image URLs (comma-separated)")
    publish_parser.add_argument("--tags", help="Tags (comma-separated)")
    publish_parser.add_argument("--json", action="store_true", help="Output raw JSON")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    if args.command == "status":
        result = check_status()
    elif args.command == "search":
        result = search_notes(args.keyword, args.sort, args.type, args.time)
    elif args.command == "detail":
        result = get_note_detail(args.feed_id, args.xsec_token, args.comments)
    elif args.command == "feeds":
        result = get_feeds()
    elif args.command == "publish":
        images = args.images.split(",")
        tags = args.tags.split(",") if args.tags else None
        result = publish_note(args.title, args.content, images, tags)

    # Unified --json output for all commands
    if getattr(args, "json", False):
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
