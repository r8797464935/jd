"""Command-line entry point: create a Facebook Page event, then invite guests
using Facebook's own invite system (POST /{event_id}/invited/{user_id}).
"""

import argparse
import os
import sys
from datetime import datetime

from .facebook import FacebookEventClient, FacebookAPIError


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Create a Facebook Page event and invite guests via Facebook's own invite system."
    )
    parser.add_argument("--name", required=True, help="Event name")
    parser.add_argument("--description", default="", help="Event description")
    parser.add_argument("--location", default=None, help="Event location (free text address)")
    parser.add_argument("--place-id", default=None, help="Facebook Place ID for the location")
    parser.add_argument("--start", required=True, help="Start datetime, ISO format e.g. 2026-09-01T18:00:00")
    parser.add_argument("--end", required=True, help="End datetime, ISO format")
    parser.add_argument("--timezone", default=None, help="IANA timezone name, e.g. America/Los_Angeles")
    parser.add_argument("--online", action="store_true", help="Mark event as online")
    parser.add_argument(
        "--invite-uids",
        required=True,
        help=(
            "Comma-separated Facebook user IDs to invite, or a path to a file with one "
            "user ID per line. Facebook's invite system works on user IDs, not emails."
        ),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Build the event/invite payloads but skip the Facebook API calls",
    )
    return parser.parse_args(argv)


def load_ids(value):
    if os.path.isfile(value):
        with open(value) as f:
            return [line.strip() for line in f if line.strip()]
    return [uid.strip() for uid in value.split(",") if uid.strip()]


def main(argv=None):
    args = parse_args(argv)

    start = datetime.fromisoformat(args.start)
    end = datetime.fromisoformat(args.end)
    invite_uids = load_ids(args.invite_uids)

    if args.dry_run:
        print("[dry-run] Skipping Facebook event creation and invite calls.")
        print(f"Would create event '{args.name}' from {start} to {end}")
        print(f"Would invite {len(invite_uids)} Facebook user(s): {', '.join(invite_uids)}")
        return

    page_id = os.environ.get("FB_PAGE_ID")
    page_token = os.environ.get("FB_PAGE_ACCESS_TOKEN")
    user_token = os.environ.get("FB_USER_ACCESS_TOKEN")
    if not (page_id and page_token):
        sys.exit("FB_PAGE_ID and FB_PAGE_ACCESS_TOKEN must be set in the environment.")
    if not user_token:
        sys.exit(
            "FB_USER_ACCESS_TOKEN must be set in the environment to send invites "
            "(a user token with the user_events permission)."
        )

    fb_client = FacebookEventClient(page_id, page_token)
    try:
        result = fb_client.create_event(
            name=args.name,
            start_time=start.isoformat(),
            end_time=end.isoformat(),
            description=args.description,
            location=args.location,
            place_id=args.place_id,
            timezone=args.timezone,
            is_online=args.online,
        )
    except FacebookAPIError as exc:
        sys.exit(f"Facebook API error: {exc}")

    event_id = result["id"]
    event_url = fb_client.get_event_url(event_id)
    print(f"Created Facebook event {event_id}: {event_url}")

    try:
        invite_results = fb_client.invite_users(event_id, invite_uids, user_token)
    except ValueError as exc:
        sys.exit(str(exc))

    succeeded = [uid for uid, r in invite_results.items() if r.get("success")]
    failed = {uid: r.get("error") for uid, r in invite_results.items() if not r.get("success")}

    print(f"Invited {len(succeeded)}/{len(invite_uids)} user(s).")
    if failed:
        print("Failed invites:")
        for uid, error in failed.items():
            print(f"  {uid}: {error}")


if __name__ == "__main__":
    main()
