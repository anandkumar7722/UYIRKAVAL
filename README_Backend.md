# Nirbhay – SHE-SHIELD Backend

> Offline-first women's safety ecosystem – FastAPI + Supabase backend.

We keep all ML models inside the Android app itself (not server-side) because the victim may have **no internet**. This backend only processes SOS payloads once they reach a device with connectivity (via Bluetooth mesh relay or direct).

---

## Quick Start

### 1. Prerequisites

- Python 3.11+
- A [Supabase](https://supabase.com) project (tables already created)
- A [Fast2SMS](https://www.fast2sms.com) account for emergency SMS

### 2. Clone & Install

```bash
git clone https://github.com/derangee/Nirbhay.git
cd Nirbhay
python -m venv venv
source venv/bin/activate      # macOS / Linux
pip install -r requirements.txt
```

### 3. Configure Environment

Copy the example below into a `.env` file in the project root:

```env
# Supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key

# Fast2SMS
FAST2SMS_API_KEY=your-fast2sms-api-key

# Google Maps
GOOGLE_MAPS_API_KEY=your-maps-key

# Tracking page URL used in SMS messages
TRACKING_BASE_URL=https://yourdomain.com/track
```

> **Never commit `.env` to version control.** It is already in `.gitignore`.

### 4. Run the Server

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Swagger docs available at: **http://localhost:8000/docs**

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/sos/relay` | Receive SOS from Bluetooth mesh relay (rate-limited) |
| `POST` | `/api/sos/trigger` | Direct SOS when victim has internet |
| `POST` | `/api/sos/location` | Live-tracking telemetry upsert |
| `POST` | `/api/sos/resolve` | Victim marks themselves safe (PIN verified) |
| `GET`  | `/` | Health check |

### Authentication Model

- **Relay & Trigger endpoints**: `victim_id` + `emergency_token` (verified against `profiles` table).
- **Resolve endpoint**: `victim_id` + `secure_pin`.
- The relay device's identity / IP is **never trusted**.

---

## Project Structure

```
Nirbhay/
├── .env                 # Secrets (git-ignored)
├── .gitignore
├── requirements.txt     # Python dependencies
├── models.py            # Pydantic request/response schemas
├── main.py              # FastAPI application & endpoints
└── README.md
```

---

## Database Schema (Supabase / PostgreSQL)

- **profiles** – user identity, `emergency_token`, `secure_pin`, FCM token
- **trusted_contacts** – per-user emergency contacts with SMS toggle
- **sos_events** – every SOS trigger (active / resolved), location, relay IP
- **live_tracking** – real-time GPS telemetry (upserted on `sos_id` PK)

Both `live_tracking` and `sos_events` are added to Supabase Realtime for live subscriptions.
