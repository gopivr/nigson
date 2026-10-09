"""Bounded private Keycloak administration. Never logs secrets or response bodies."""
import json
import ssl
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Admin:
    def __init__(self, base: str, ca: str):
        parsed = urllib.parse.urlsplit(base)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
            raise ValueError("PRIVATE_HTTPS_ADMIN_REQUIRED")
        self.base, self.token = base, None
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=ca)), NoRedirect())

    def request(self, path: str, method="GET", data=None, form=False, statuses=(200, 201, 204)):
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("ADMIN_PATH_REJECTED")
        raw = None if data is None else (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        headers = {"Content-Type": "application/x-www-form-urlencoded" if form else "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        try:
            with self.http.open(urllib.request.Request(self.base + path, data=raw, headers=headers, method=method), timeout=10) as result:
                body = result.read(524289)
                if len(body) > 524288 or result.status not in statuses:
                    raise ValueError("ADMIN_RESPONSE_REJECTED")
                return json.loads(body) if body else None
        except urllib.error.HTTPError as rejected:
            if rejected.code in statuses:
                return None
            raise ValueError("ADMIN_HTTP_REJECTED_" + str(rejected.code)) from None

    def login(self, username: str, password: str, otp: str | None = None):
        body = {"grant_type": "password", "client_id": "admin-cli", "username": username, "password": password}
        if otp: body["totp"] = otp
        response = self.request("/realms/master/protocol/openid-connect/token", "POST", body, form=True)
        self.token = response["access_token"]

    def provision(self, policy: dict, secrets: dict[str, str]):
        if set(secrets) != {c["clientId"] for c in policy["clients"]} or any(not isinstance(v, str) or not 32 <= len(v) <= 512 for v in secrets.values()):
            raise ValueError("EXPLICIT_CLIENT_SECRETS_REQUIRED")
        name = policy["realm"]
        realms = self.request("/admin/realms")
        if name not in {x["realm"] for x in realms}:
            self.request("/admin/realms", "POST", policy)
        else:
            # Update policy fields, not vendor state/users/signing keys or unrelated realms.
            self.request("/admin/realms/" + name, "PUT", {k: v for k, v in policy.items() if k not in {"clients", "clientScopes", "roles"}})
        prefix = "/admin/realms/" + name
        scope_ids = {s["name"]: s["id"] for s in self.request(prefix + "/client-scopes")}
        for desired in policy["clientScopes"]:
            if desired["name"] not in scope_ids:
                self.request(prefix + "/client-scopes", "POST", desired)
            else:
                self.request(prefix + "/client-scopes/" + scope_ids[desired["name"]], "PUT", desired)
        scope_ids = {s["name"]: s["id"] for s in self.request(prefix + "/client-scopes")}
        for desired in policy["clientScopes"]:
            self.mappers(prefix + "/client-scopes/" + scope_ids[desired["name"]], desired.get("protocolMappers", []))
        roles = {role["name"] for role in self.request(prefix + "/roles")}
        for role in policy["roles"]["realm"]:
            if role["name"] not in roles: self.request(prefix + "/roles", "POST", role)
        clients = self.request(prefix + "/clients")
        for desired in policy["clients"]:
            found = [c for c in clients if c["clientId"] == desired["clientId"]]
            secret = secrets.get(desired["clientId"])
            if not secret:
                raise ValueError("CLIENT_SECRET_REQUIRED")
            body = {**desired, "secret": secret}
            if not found:
                # An uncertain POST is not retried: re-run reads stable clientId before a new create.
                self.request(prefix + "/clients", "POST", body)
            else:
                self.request(prefix + "/clients/" + found[0]["id"], "PUT", body)
            cid = self.request(prefix + "/clients?" + urllib.parse.urlencode({"clientId": desired["clientId"]}))[0]["id"]
            self.mappers(prefix + "/clients/" + cid, desired.get("protocolMappers", []))
            wanted = set(desired.get("defaultClientScopes", []))
            if desired["clientId"] == "nigson-bff":
                continue
            assigned = self.request(prefix + "/clients/" + cid + "/default-client-scopes")
            for scope in assigned:
                if scope["name"] not in wanted:
                    self.request(prefix + "/clients/" + cid + "/default-client-scopes/" + scope["id"], "DELETE")
            for scope in wanted:
                self.request(prefix + "/clients/" + cid + "/default-client-scopes/" + scope_ids[scope], "PUT")
        self.staff_flow(prefix)
        # Administrative events have separate vendor retention, including the private master realm.
        master = self.request("/admin/realms/master")
        self.request("/admin/realms/master", "PUT", {
            "eventsEnabled": True, "eventsExpiration": policy["eventsExpiration"], "eventsListeners": [],
            "adminEventsEnabled": True, "adminEventsDetailsEnabled": False,
            "attributes": {**master.get("attributes", {}), "adminEventsExpiration": str(policy["eventsExpiration"])}
        })

    def mappers(self, prefix, desired):
        current = {m["name"]: m for m in self.request(prefix + "/protocol-mappers/models")}
        wanted = {m["name"] for m in desired}
        for mapper in desired:
            if mapper["name"] in current:
                old = current[mapper["name"]]
                self.request(prefix + "/protocol-mappers/models/" + old["id"], "PUT", {**mapper, "id": old["id"]})
            else:
                self.request(prefix + "/protocol-mappers/models", "POST", mapper)
        for name, mapper in current.items():
            if name not in wanted: self.request(prefix + "/protocol-mappers/models/" + mapper["id"], "DELETE")

    def staff_flow(self, prefix):
        alias = "nigson-browser"
        flows = self.request(prefix + "/authentication/flows")
        if alias not in {x["alias"] for x in flows}:
            self.request(prefix + "/authentication/flows", "POST", {"alias": alias, "providerId": "basic-flow", "topLevel": True, "builtIn": False})
        def ensure(parent, provider=None, child=None):
            path = prefix + "/authentication/flows/" + parent + "/executions"
            current = self.request(path)
            found = [x for x in current if x.get("level", 0)==0 and (x.get("providerId")==provider if provider else x.get("displayName")==child)]
            if len(found)>1: raise ValueError("IDENTITY_FLOW_DUPLICATE_DRIFT")
            if not found:
                if provider: self.request(path+"/execution","POST",{"provider":provider})
                else: self.request(path+"/flow","POST",{"alias":child,"type":"basic-flow","provider":"basic-flow"})
        ensure(alias,provider="auth-cookie")
        ensure(alias,child="nigson-forms")
        ensure("nigson-forms",provider="auth-username-password-form")
        ensure("nigson-forms",child="nigson-staff-mfa")
        ensure("nigson-staff-mfa",provider="conditional-user-role")
        ensure("nigson-staff-mfa",provider="auth-otp-form")
        executions = self.request(prefix + "/authentication/flows/" + alias + "/executions")
        for execution in executions:
            provider = execution.get("providerId")
            requirement = "REQUIRED"
            if provider == "auth-cookie" or execution.get("displayName") == "nigson-forms":
                requirement = "ALTERNATIVE"
            elif execution.get("displayName") == "nigson-staff-mfa":
                requirement = "CONDITIONAL"
            self.request(prefix + "/authentication/flows/" + alias + "/executions", "PUT", {**execution, "requirement": requirement})
            if provider == "conditional-user-role":
                desired = {"alias": "nigson-staff-role-condition", "config": {"condUserRole": "nigson-staff-identity"}}
                if execution.get("authenticationConfig"):
                    self.request(prefix + "/authentication/config/" + execution["authenticationConfig"], "PUT", desired)
                else:
                    self.request(prefix + "/authentication/executions/" + execution["id"] + "/config", "POST", desired)
            if provider == "auth-otp-form":
                desired = {"alias": "nigson-otp-reference", "config": {"default.reference.value": "otp", "default.reference.maxAge": "900"}}
                if execution.get("authenticationConfig"):
                    self.request(prefix + "/authentication/config/" + execution["authenticationConfig"], "PUT", desired)
                else:
                    self.request(prefix + "/authentication/executions/" + execution["id"] + "/config", "POST", desired)
        self.request(prefix, "PUT", {"browserFlow": alias})
