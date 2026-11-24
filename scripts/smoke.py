"""Exercise the public API with a disposable account and remove it afterward."""

import argparse
import http.cookiejar
import json
import secrets
import urllib.error
import urllib.parse
import urllib.request


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


class Client:
    def __init__(self, base_url, loopback_http):
        self.base_url = base_url
        self.jar = http.cookiejar.CookieJar()
        if loopback_http:
            self.jar.set_policy(http.cookiejar.DefaultCookiePolicy(secure_protocols=("https", "http")))
        self.opener = urllib.request.build_opener(
            NoRedirects(), urllib.request.HTTPCookieProcessor(self.jar))
        self.token = {}

    def request(self, method, path, expected, payload=None, csrf=True):
        headers = {"Accept": "application/json"}
        data = None
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if csrf and method in {"POST", "PUT", "PATCH", "DELETE"}:
            headers[self.token["headerName"]] = self.token["token"]
        request = urllib.request.Request(self.base_url + path, data, headers, method=method)
        try:
            response = self.opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            check(response.status == expected,
                  f"{method} {path} returned {response.status}, expected {expected}")
            content = response.read()
            return json.loads(content) if content else None, response.headers

    def refresh_csrf(self):
        self.token, headers = self.request("GET", "/api/auth/csrf", 200)
        check(headers.get("Cache-Control") == "no-store", "CSRF response is cacheable")
        return headers

    def session_id(self):
        return next(cookie.value for cookie in self.jar if cookie.name == "SESSION")

    def login(self, credentials):
        self.refresh_csrf()
        old_session = self.session_id()
        old_token = self.token["token"]
        self.request("POST", "/api/auth/login", 200, credentials)
        check(self.session_id() != old_session, "Login did not rotate the session ID")
        self.refresh_csrf()
        check(self.token["token"] != old_token, "Login did not rotate the CSRF token")


def smoke(url, loopback_http):
    client = Client(url, loopback_http)
    credentials = {"username": "smoke_" + secrets.token_hex(10),
                   "password": secrets.token_urlsafe(24)}
    registered = False
    signed_in = False
    try:
        client.request("GET", "/api/health", 200)
        readiness, _ = client.request("GET", "/api/ready", 200)
        check(readiness["database"] == "reachable", "Readiness did not verify the database")
        client.request("GET", "/api/auth/me", 401)
        headers = client.refresh_csrf()
        cookies = headers.get_all("Set-Cookie", [])
        check(any(all(flag in cookie.lower().split("; ") for flag in
                      ("secure", "httponly", "samesite=lax")) for cookie in cookies),
              "Production session cookie flags were missing")
        client.request("POST", "/api/auth/register", 201,
                       {**credentials, "timezone": "UTC"})
        registered = True
        client.login(credentials)
        signed_in = True
        client.request("GET", "/api/auth/me", 200)
        profile, _ = client.request("GET", "/api/users/me/statistics", 200)
        check(profile["totalHabits"] == 0, "A new profile should start without habits")
        client.request("POST", "/api/habits", 403, {"name": "Smoke"}, csrf=False)

        habit, _ = client.request("POST", "/api/habits", 201,
                                  {"name": "Smoke", "kind": "counter", "target": 2})
        path = "/api/habits/" + habit["id"]
        habits, _ = client.request("GET", "/api/habits", 200)
        check(any(item["id"] == habit["id"] for item in habits), "Habit missing from list")
        client.request("PUT", path, 200, {"name": "Smoke updated"})
        for method, count in (("increment", 1), ("increment", 2), ("decrement", 1)):
            result, _ = client.request("POST", path + "/" + method, 200)
            check(result["count"] == count, "Unexpected progress")
        heatmap, _ = client.request("GET", path + "/heatmap?days=1", 200)
        statistics, _ = client.request("GET", path + "/statistics?days=1", 200)
        activity, _ = client.request("GET", "/api/activity?page=0&size=2", 200)
        check(heatmap[0]["count"] == 1 and statistics["currentStreak"] == 1,
              "Heatmap or statistics disagree with recorded progress")
        check(activity["totalElements"] == 3 and activity["hasNext"], "Activity page is incomplete")
        client.request("DELETE", path, 204)
        habits, _ = client.request("GET", "/api/habits", 200)
        check(not habits, "Deleted habit remained visible")
        client.request("POST", "/api/auth/logout", 204)
        signed_in = False
        client.request("GET", "/api/auth/me", 401)
    finally:
        if registered:
            if not signed_in:
                client.login(credentials)
            client.request("DELETE", "/api/users/me", 204,
                           {"currentPassword": credentials["password"]})
            client.request("GET", "/api/auth/me", 401)
    print("Smoke passed: health, readiness, CSRF, sessions, CRUD, progress, statistics, logout and account cleanup")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="Base application URL")
    parser.add_argument("--loopback-http", action="store_true",
                        help="Allow HTTP and Secure cookies for a local container or JAR check")
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.url)
    loopback = (args.loopback_http and parsed.scheme == "http"
                and parsed.hostname in {"127.0.0.1", "::1", "localhost"})
    if (not parsed.hostname or parsed.username or parsed.query or parsed.fragment
            or parsed.path not in {"", "/"} or (parsed.scheme != "https" and not loopback)):
        parser.error("Use a bare HTTPS application URL or explicit loopback HTTP.")
    smoke(args.url.rstrip("/"), loopback)


if __name__ == "__main__":
    main()
