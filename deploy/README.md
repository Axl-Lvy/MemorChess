# Mini PC hosting runbook

Manual setup steps for hosting MemorChess on the mini PC. Nothing here is automated; this is a
runbook, not a script, because Claude has no SSH or other access to the mini PC.

## 1. Prerequisites

- Docker and the Docker Compose plugin installed.
- `adnanh/webhook` installed at `/usr/local/bin/webhook` (see its release page for the right
  binary for your architecture).
- A Cloudflare account with `memorchess.axl-lvy.fr` on Cloudflare DNS.

## 2. Cloudflare Tunnel

Create a tunnel (`cloudflared tunnel create memorchess`) and two ingress rules pointing at this
host:
- `memorchess.axl-lvy.fr` -> `http://server:8080` (the Compose service name, once the stack in
  step 4 is up)
- `deploy.memorchess.axl-lvy.fr` -> `http://localhost:9000` (the webhook service from step 3,
  which runs on the host, not in Compose)

Also add a Cloudflare Cache Rule caching `*.wasm` responses from `memorchess.axl-lvy.fr` at the
edge (`application/wasm` is not cached by Cloudflare's defaults) — otherwise every visitor pulls
the wasm payload straight from this machine's upload bandwidth on every fresh visit.

Get the tunnel token: `cloudflared tunnel token memorchess`. You'll put it in `.env` in step 4.

## 3. Deploy webhook

```bash
mkdir -p /opt/memorchess/deploy
cp deploy.sh webhook-hooks.yaml.example deploy-webhook.service /opt/memorchess/deploy/
cd /opt/memorchess/deploy
mv webhook-hooks.yaml.example webhook-hooks.yaml
# Edit webhook-hooks.yaml: replace REPLACE_WITH_DEPLOY_WEBHOOK_TOKEN with a token you generate
# (e.g. `openssl rand -hex 32`) — the same value goes in .env's DEPLOY_WEBHOOK_TOKEN below, and
# in this repo's DEPLOY_WEBHOOK_TOKEN GitHub Actions secret.
chmod 600 webhook-hooks.yaml
sudo cp deploy-webhook.service /etc/systemd/system/
sudo systemctl enable --now deploy-webhook
```

## 4. The Compose stack

```bash
cp docker-compose.yml .env.example /opt/memorchess/deploy/
cd /opt/memorchess/deploy
mv .env.example .env
chmod 600 .env
# Fill in every value in .env (see the comments in the file for where each one comes from).
docker compose up -d
```

## 5. Verify

- `curl https://memorchess.axl-lvy.fr/health` returns `ok`.
- `curl https://memorchess.axl-lvy.fr/` returns the frontend's `index.html`.
- A push to this repo's `master` branch eventually (within ~30s) restarts the `server` container
  (`docker compose logs -f server` on the mini PC to watch it happen).

## 6. Before decommissioning GitHub Pages

Once the above is confirmed working end to end (sign-in, sync), and only then:
- Add `https://memorchess.axl-lvy.fr` to Logto's SPA app redirect-URI allowlist and its separate
  CORS-allowed-origins field.
- Disable GitHub Pages in this repo's Settings (deleting the old workflow file alone does not take
  the published site down).
- Remove the GitHub Pages origin from Logto's and Lichess's allowlists once the new origin is
  confirmed working.
