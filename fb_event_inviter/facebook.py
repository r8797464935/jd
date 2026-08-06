"""Client for creating Facebook Page events via the Graph API.

Facebook removed the ability to create events on a personal profile from the
Graph API in v2.9 (2018). Events can only be created on a Facebook Page that
the calling app manages, using a Page access token whose app has been granted
the ``pages_manage_events`` permission through Facebook App Review.
"""

import requests

GRAPH_API_VERSION = "v19.0"
GRAPH_API_BASE = f"https://graph.facebook.com/{GRAPH_API_VERSION}"


class FacebookAPIError(Exception):
    """Raised when the Graph API returns an error payload."""


class FacebookEventClient:
    def __init__(self, page_id, page_access_token, session=None):
        if not page_id:
            raise ValueError("page_id is required")
        if not page_access_token:
            raise ValueError("page_access_token is required")
        self.page_id = page_id
        self.page_access_token = page_access_token
        self._session = session or requests.Session()

    def create_event(self, name, start_time, end_time=None, description="",
                      location=None, place_id=None, timezone=None, is_online=False):
        """Create an event on the configured Page.

        start_time / end_time: ISO 8601 strings, e.g. "2026-09-01T18:00:00-0700".
        location: free-text address string (mutually usable alongside place_id).
        place_id: numeric ID of an existing Facebook Place node.
        Returns the parsed JSON response, e.g. {"id": "123456789"}.
        """
        payload = {
            "name": name,
            "start_time": start_time,
            "access_token": self.page_access_token,
        }
        if description:
            payload["description"] = description
        if end_time:
            payload["end_time"] = end_time
        if location:
            payload["location"] = location
        if place_id:
            payload["place_id"] = place_id
        if timezone:
            payload["timezone"] = timezone
        if is_online:
            payload["is_online"] = "true"

        url = f"{GRAPH_API_BASE}/{self.page_id}/events"
        response = self._session.post(url, data=payload, timeout=30)
        try:
            data = response.json()
        except ValueError:
            response.raise_for_status()
            raise FacebookAPIError(f"Unexpected non-JSON response: {response.text}")

        if response.status_code >= 400 or "error" in data:
            raise FacebookAPIError(data.get("error", data))
        return data

    @staticmethod
    def get_event_url(event_id):
        return f"https://www.facebook.com/events/{event_id}/"
