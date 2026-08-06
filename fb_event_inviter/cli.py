"""Command-line entry point: create a Facebook Page event, then email invites."""

import argparse
import os
import sys
from datetime import datetime

from .facebook import FacebookEventClient, FacebookAPIError
from .email_invite import EmailInviter


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Create a Facebook Page event and email invitations to a recipient list."
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
        "--recipients",
        required=True,
        help="Comma-separated email addresses, or a path to a file with one email per line",
    )
    parser.add_argument("--subject", default=None, help="Email subject (defaults to the event name)")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Build the event/email payloads but skip the Facebook API call and the email send",
    )
    return parser.parse_args(argv)


def load_recipients(value):
    if os.path.isfile(value):
        with open(value) as f:
            return [line.strip() for line in f if line.strip()]
    return [addr.strip() for addr in value.split(",") if addr.strip()]


def main(argv=None):
    args = parse_args(argv)

    start = datetime.fromisoformat(args.start)
    end = datetime.fromisoformat(args.end)
    recipients = load_recipients(args.recipients)
    subject = args.subject or f"You're invited: {args.name}"

    if args.dry_run:
        print("[dry-run] Skipping Facebook event creation and email send.")
        print(f"Would create event '{args.name}' from {start} to {end}")
        print(f"Would email {len(recipients)} recipient(s): {', '.join(recipients)}")
        return

    page_id = os.environ.get("FB_PAGE_ID")
    page_token = os.environ.get("FB_PAGE_ACCESS_TOKEN")
    if not (page_id and page_token):
        sys.exit("FB_PAGE_ID and FB_PAGE_ACCESS_TOKEN must be set in the environment.")

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

    smtp_host = os.environ.get("SMTP_HOST")
    smtp_port = int(os.environ.get("SMTP_PORT", "587"))
    smtp_user = os.environ.get("SMTP_USER")
    smtp_password = os.environ.get("SMTP_PASSWORD")
    sender_name = os.environ.get("SENDER_NAME")

    if not (smtp_host and smtp_user and smtp_password):
        sys.exit("SMTP_HOST, SMTP_USER, and SMTP_PASSWORD must be set in the environment.")

    inviter = EmailInviter(smtp_host, smtp_port, smtp_user, smtp_password)
    results = inviter.send_invite(
        recipients=recipients,
        subject=subject,
        event_name=args.name,
        description=args.description,
        location=args.location,
        start=start,
        end=end,
        event_url=event_url,
        sender=smtp_user,
        sender_name=sender_name,
    )
    print(f"Sent invites to {len(results)} recipient(s).")


if __name__ == "__main__":
    main()
