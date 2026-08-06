# jd

Create a Facebook (Page) event via the Graph API, then invite guests using
Facebook's own invite system — no email server involved.

## Important: Facebook API limitations

**Creating the event.** Facebook removed the ability to create events on a
**personal profile** from the Graph API back in v2.9 (2018). Today, events
can only be created via the API on a **Facebook Page**, using:

- A **Page access token** (not a user token)
- An app that has been granted the `pages_manage_events` permission through
  [Facebook App Review](https://developers.facebook.com/docs/app-review)

**Inviting guests.** This uses `POST /{event_id}/invited/{user_id}` — the
same edge the Facebook UI calls when you click "Invite Friends". It is
**not** an email system: invites are addressed to Facebook user IDs, and
Facebook (not this code) decides how/whether to notify that person.

- Requires a **user access token** (not a Page token) belonging to a
  host/admin of the event, granted the `user_events` permission.
- Facebook locked `user_events` down in its 2018 platform changes. Apps
  created since then are effectively unable to get it approved through App
  Review, so in practice this call will very likely fail with an OAuth
  permission error (`"Invalid Scope"`) unless you're using a legacy app that
  still holds the permission.
- The invited person generally must be a Facebook friend of the token
  holder.
- This edge is documented for user-created events; Page-owned events (the
  only kind this API can create) are public/discovery-based and may reject
  per-user invites outright even with a valid token.

If `invite_users` fails for your app/token, the practical fallback is to
invite people manually from the Facebook UI after the event is created —
`create_event` still saves you that part, and prints the event URL.

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
  --invite-uids "1000000001,1000000002"
```

`--invite-uids` also accepts a path to a text file with one Facebook user ID
per line.

Add `--dry-run` to preview the event/invite payload without calling the
Facebook API.

### Required environment variables (non-dry-run)

| Variable | Description |
|---|---|
| `FB_PAGE_ID` | ID of the Facebook Page that will own the event |
| `FB_PAGE_ACCESS_TOKEN` | Page access token with `pages_manage_events` |
| `FB_USER_ACCESS_TOKEN` | User access token with `user_events`, used to send invites |

## Library usage

```python
from datetime import datetime
from fb_event_inviter import FacebookEventClient

fb = FacebookEventClient(page_id="...", page_access_token="...")
event = fb.create_event(
    name="Launch Party",
    start_time="2026-09-01T18:00:00-07:00",
    end_time="2026-09-01T21:00:00-07:00",
    description="Come celebrate!",
    location="123 Main St",
)
event_url = fb.get_event_url(event["id"])

results = fb.invite_users(event["id"], ["1000000001", "1000000002"], user_access_token="...")
# {"1000000001": {"success": True}, "1000000002": {"success": False, "error": {...}}}
```

## Optional: email fallback

If you decide Facebook's invite system doesn't fit your use case (e.g. your
app can't get `user_events` approved, or you want to reach people who aren't
Facebook friends of the token holder), `fb_event_inviter/email_invite.py`
still contains an SMTP-based inviter that builds a proper `.ics` calendar
attachment. It's not wired into the CLI by default — use it directly as a
library if needed:

```python
from fb_event_inviter import EmailInviter

inviter = EmailInviter("smtp.example.com", 587, "you@example.com", "app-password")
inviter.send_invite(
    recipients=["a@example.com"],
    subject="You're invited: Launch Party",
    event_name="Launch Party",
    description="Come celebrate!",
    location="123 Main St",
    start=datetime.fromisoformat("2026-09-01T18:00:00-07:00"),
    end=datetime.fromisoformat("2026-09-01T21:00:00-07:00"),
    event_url=event_url,
)
```

## Tests

```bash
python -m unittest discover tests
```
