# jd

Create a Facebook (Page) event via the Graph API, then email invitations with
a calendar (`.ics`) attachment to a list of recipients.

## Important: Facebook API limitations

Facebook removed the ability to create events on a **personal profile** from
the Graph API back in v2.9 (2018). Today, events can only be created via the
API on a **Facebook Page**, using:

- A **Page access token** (not a user token)
- An app that has been granted the `pages_manage_events` permission through
  [Facebook App Review](https://developers.facebook.com/docs/app-review)

If you only need a personal-profile event, you'll have to create it manually
in the Facebook UI — the invitation-emailing part of this tool still works
fine, just skip `--dry-run` false / pass `--dry-run` and email the group
yourself, or point the code at your own event URL.

## Install

```bash
pip install -r requirements.txt
cp .env.example .env   # then fill in your values and `export $(cat .env | xargs)`
```

## Usage

```bash
python -m fb_event_inviter.cli \
  --name "Launch Party" \
  --description "Come celebrate the launch with us!" \
  --location "123 Main St, Springfield" \
  --start 2026-09-01T18:00:00-07:00 \
  --end 2026-09-01T21:00:00-07:00 \
  --recipients "a@example.com,b@example.com"
```

`--recipients` also accepts a path to a text file with one email address per
line.

Add `--dry-run` to preview the event/email payload without calling the
Facebook API or sending mail.

### Required environment variables (non-dry-run)

| Variable | Description |
|---|---|
| `FB_PAGE_ID` | ID of the Facebook Page that will own the event |
| `FB_PAGE_ACCESS_TOKEN` | Page access token with `pages_manage_events` |
| `SMTP_HOST` / `SMTP_PORT` | SMTP server for sending invites |
| `SMTP_USER` / `SMTP_PASSWORD` | SMTP login credentials |
| `SENDER_NAME` | Optional display name for the "From" header |

## Library usage

```python
from datetime import datetime
from fb_event_inviter import FacebookEventClient, EmailInviter

fb = FacebookEventClient(page_id="...", page_access_token="...")
event = fb.create_event(
    name="Launch Party",
    start_time="2026-09-01T18:00:00-07:00",
    end_time="2026-09-01T21:00:00-07:00",
    description="Come celebrate!",
    location="123 Main St",
)
event_url = fb.get_event_url(event["id"])

inviter = EmailInviter("smtp.example.com", 587, "you@example.com", "app-password")
inviter.send_invite(
    recipients=["a@example.com", "b@example.com"],
    subject="You're invited: Launch Party",
    event_name="Launch Party",
    description="Come celebrate!",
    location="123 Main St",
    start=datetime.fromisoformat("2026-09-01T18:00:00-07:00"),
    end=datetime.fromisoformat("2026-09-01T21:00:00-07:00"),
    event_url=event_url,
)
```

The email includes an `.ics` calendar attachment so most mail clients (Gmail,
Outlook, Apple Mail) will render it as an "Accept / Decline" style invite.

## Tests

```bash
python -m unittest discover tests
```
