"""Send calendar-style email invitations (with an .ics attachment) over SMTP."""

import smtplib
import uuid
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText


def _format_ics_datetime(dt):
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc)
        return dt.strftime("%Y%m%dT%H%M%SZ")
    return dt.strftime("%Y%m%dT%H%M%S")


def _escape_ics_text(text):
    return (text or "").replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")


def build_ics(event_name, description, location, start, end, organizer_email,
              event_url=None, uid=None):
    """Build an iCalendar (RFC 5545) VEVENT payload for the given event."""
    uid = uid or f"{uuid.uuid4()}@fb-event-inviter"
    dtstamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")

    desc = description or ""
    if event_url:
        desc = f"{desc}\n\nFacebook event: {event_url}".strip()

    lines = [
        "BEGIN:VCALENDAR",
        "PRODID:-//fb-event-inviter//EN",
        "VERSION:2.0",
        "METHOD:REQUEST",
        "CALSCALE:GREGORIAN",
        "BEGIN:VEVENT",
        f"UID:{uid}",
        f"DTSTAMP:{dtstamp}",
        f"DTSTART:{_format_ics_datetime(start)}",
        f"DTEND:{_format_ics_datetime(end)}",
        f"SUMMARY:{_escape_ics_text(event_name)}",
        f"DESCRIPTION:{_escape_ics_text(desc)}",
        f"LOCATION:{_escape_ics_text(location)}",
        f"ORGANIZER:mailto:{organizer_email}",
        "STATUS:CONFIRMED",
        "SEQUENCE:0",
        "END:VEVENT",
        "END:VCALENDAR",
    ]
    return "\r\n".join(lines)


class EmailInviter:
    def __init__(self, smtp_host, smtp_port, smtp_user, smtp_password, use_tls=True):
        self.smtp_host = smtp_host
        self.smtp_port = smtp_port
        self.smtp_user = smtp_user
        self.smtp_password = smtp_password
        self.use_tls = use_tls

    def _build_message(self, recipient, sender, sender_name, subject, body, ics_content):
        msg = MIMEMultipart("mixed")
        msg["Subject"] = subject
        msg["From"] = f"{sender_name} <{sender}>" if sender_name else sender
        msg["To"] = recipient

        msg.attach(MIMEText(body, "plain"))

        ics_part = MIMEText(ics_content, "calendar; method=REQUEST; name=invite.ics")
        ics_part.add_header("Content-Disposition", "attachment", filename="invite.ics")
        msg.attach(ics_part)
        return msg

    def send_invite(self, recipients, subject, event_name, description, location,
                     start, end, event_url=None, sender=None, sender_name=None):
        if not recipients:
            raise ValueError("recipients must be a non-empty list of email addresses")

        sender = sender or self.smtp_user
        ics_content = build_ics(event_name, description, location, start, end, sender, event_url)

        body_lines = [f"You're invited to {event_name}!", ""]
        if description:
            body_lines.extend([description, ""])
        if location:
            body_lines.append(f"Location: {location}")
        body_lines.append(f"When: {start.strftime('%Y-%m-%d %H:%M')} - {end.strftime('%Y-%m-%d %H:%M')}")
        if event_url:
            body_lines.append(f"Facebook event: {event_url}")
        body = "\n".join(body_lines)

        results = {}
        with smtplib.SMTP(self.smtp_host, self.smtp_port) as server:
            if self.use_tls:
                server.starttls()
            if self.smtp_user and self.smtp_password:
                server.login(self.smtp_user, self.smtp_password)

            for recipient in recipients:
                msg = self._build_message(recipient, sender, sender_name, subject, body, ics_content)
                server.sendmail(sender, [recipient], msg.as_string())
                results[recipient] = "sent"
        return results
