# Pi VPN Gateway — Project Definition

**Project:** Whole-house outbound VPN gateway for the Klasmeier home LAN  
**Last updated:** June 28, 2026  
**Status:** Planning — new Pi 5 (2 GB) not yet installed  

This document is the single source of truth for *what* we are building and *why*. Detailed network inventory, exact IP assignments, and step-by-step commands will be finalized closer to build time.

**Out of scope for this doc:** Phase 5–7 operational tuning (throughput limits, SD wear, dashboard layout) — decide during implementation. Local `.docx` notes in this folder are **not** maintained alongside this file; treat them as optional offline reference only.

### How to read this document

| Sections | Topic |
|----------|--------|
| **1–2** | Goals and why the R8000 DHCP workaround is needed |
| **3–5** | Hardware, network layout, data flow (§4.1), gateway IP |
| **6–8** | Device roles, DHCP migration, DNS policy |
| **9–10** | VPN services (Obscura + inbound), routing and firewall (§10.7–10.8 reference rules) |
| **11–11.1** | Management API, UI, and Prometheus/Grafana metrics |
| **12** | Build phases (execute in order); **§12.1** pre-UI manual ops; **§12.2** registry backup to NAS |
| **13–15** | Verification, rollback, risks |
| **16–18** | Open items, related files, quick reference |

### Table of contents

1. [Goal](#1-goal)  
2. [Why we need this approach](#2-why-we-need-this-approach)  
3. [Hardware](#3-hardware)  
4. [Network layout](#4-network-layout-conceptual) · [4.1 Data flow](#41-data-flow--through-the-nighthawk-detailed)  
5. [Gateway IP address](#5-gateway-ip-address)  
6. [Devices and roles](#6-devices-and-roles-current-understanding)  
7. [DHCP migration](#7-dhcp-migration-strategy)  
8. [Device policy](#8-device-policy--opt-in-vpn-and-pi-hole) · [8.5 WiFi vs remote](#85-device-identity-wifi-vs-home-access-vpn)  
9. [VPN services](#9-vpn-services-gateway-pi-runs-both)  
10. [Routing and firewall](#10-routing-and-firewall-critical-design) · [10.7 Self-traffic](#107-gateway-host-self-traffic) · [10.8 Reference ruleset](#108-reference-firewall-ruleset-starter)  
11. [Management API and UI](#11-management-api-and-web-ui-split-architecture) · [11.1 Metrics](#111-per-device-traffic-history-prometheus--grafana)  
12. [Build phases](#12-build-phases) · [12.1 Pre-UI ops](#121-pre-ui-manual-operations-phases-35) · [12.2 NAS backup](#122-registry-backup-raspinas)  
13. [Verification checklist](#13-verification-checklist) · [A/B throughput test](#gateway-throughput-ab-test-build-baseline)  
14. [Rollback plan](#14-rollback-plan)  
15. [Risks and tradeoffs](#15-risks-and-tradeoffs)  
16. [Open items](#16-open-items)  
17. [Related files](#17-related-files-in-this-folder)  
18. [Quick reference](#18-quick-reference-fill-in-at-build-time)

---

## 1. Goal

Install a dedicated **Raspberry Pi 5 (2 GB)** as a **whole-house VPN gateway** and **remote-access VPN server**. After setup:

- **VPN (Obscura)** and **Pi-hole** are **opt-in per device** via the management UI — not enabled for everyone on day one (§8).
- **New devices** inherit **global defaults** (initially: no VPN, no Pi-hole) so cutover behaves like today until you enable features one device at a time.
- **Phone and laptop away from home** connect to the **gateway Pi** via inbound WireGuard — LAN access plus protected browsing when using the home VPN profile (§9).
- **Internal LAN traffic** stays local — does not go through Obscura regardless of VPN opt-in.
- **DHCP** currently handled by the Netgear R8000 is **migrated to the gateway Pi**, preserving reservations (IP/MAC/hostname) but **not** forcing VPN or Pi-hole on import.
- **Management app** — API on gateway, web UI on PiSensors (§11); built after core gateway works (Phase 6).

We are **not** replacing the R8000. It remains the WiFi access point and physical switch. The Pi becomes the **default gateway**, **DHCP server**, and **inbound VPN endpoint** for the LAN.

**Migration note:** Inbound WireGuard currently runs on PiHole-Main (`.4`). It moves to the gateway Pi. WireGuard client configs on phone and laptop get updated to point at the gateway (same home public IP, new server keys/config).

**Outbound VPN provider:** [Obscura](https://obscura.com/) (WireGuard). Chosen over Mullvad because Obscura accepts US-based payment. The gateway runs one Obscura WireGuard config; traffic exits via Obscura’s relay architecture (independent Mullvad exit hop — no separate Mullvad account).

---

## 2. Why we need this approach

The Netgear Nighthawk X6 R8000 **cannot** tell clients to use a custom default gateway. When it runs DHCP, it always advertises **itself** (`192.168.1.1`) as the gateway. There is no setting to change that.

So we cannot simply “point everyone at the Pi” from the router’s DHCP screen. The workable approach is:

1. Run **dnsmasq** on the gateway Pi.
2. Have dnsmasq hand out the **Pi’s IP** as the default gateway.
3. **Disable DHCP** on the R8000 (only after the Pi’s DHCP is tested and working).

This is a deliberate cutover step — not something to do on day one.

---

## 3. Hardware

| Item | Detail |
|------|--------|
| **Gateway Pi** | Raspberry Pi 5, **2 GB** — dedicated to this role (shipping, not installed yet) |
| **Existing Pi5Desktop** | Stays at `192.168.1.42` — **not** repurposed; separate 8 GB dev machine |
| **Router** | Netgear Nighthawk X6 R8000 at `192.168.1.1` |
| **PSU** | Official Pi 5 27 W USB-C supply — required for 24/7 routing load |
| **Storage** | 64 GB+ microSD (A2/U3/V30 class recommended) |
| **Cooling** | Active cooling case — Pi 5 runs warm under sustained traffic |
| **Network** | **Wired Ethernet only** — do not use WiFi for the gateway |
| **Management UI** | Hosted on PiSensors (`.26`) — not on the gateway Pi (see §11) |

---

## 4. Network layout (conceptual)

```
                    Internet
                        │
                   [ ISP modem ]
                        │
              ┌─────────┴─────────┐
              │  R8000 (.1)     │
              │  WiFi + switch  │
              │  Port fwd :51820│
              │  DHCP OFF later │
              └─────────┬───────┘
                        │ LAN (192.168.1.0/24)
         ┌──────────────┼──────────────┐
         │              │              │
   [ Gateway Pi ]  [ Pi-hole .4 ]  [ All other devices ]
   GATEWAY_IP      [ RocPiHole .11]  phones, TVs, Pis, etc.
   Obscura client   [ Prometheus .16]
   Inbound VPN srv  [ NAS, HA, cams… ]
   dnsmasq + NAT
         │
         ├── Outbound: wg0 ──► Obscura ──► Internet
         └── Inbound:  wg1 ◄── phone / laptop (remote)
```

### At home — normal LAN traffic (summary)

```
Device → Nighthawk (switch) → Gateway Pi → Nighthawk (WAN) → Obscura → Internet
```

See **§4.1** for the full path and why traffic crosses the Nighthawk twice.

### Away from home — remote access (phone / laptop)

```
Phone → home public IP:51820 → R8000 port fwd → Gateway wg1
         ├─ dest 192.168.1.0/24  →  LAN (NAS, HA, cameras, Pi-hole admin…)
         └─ dest internet         →  wg0 → Obscura → Internet  ✓ protected
```

One WireGuard connection from the client handles both home LAN access and protected web browsing. **Remote clients always get Obscura + Pi-hole** while connected (§8.5) — independent of WiFi toggles.

### DNS path

```
Default:  Device → public DNS (1.1.1.1 / 8.8.8.8) → internet
Opt-in:   Device → Pi-hole (.11 / .4) → upstream → internet
```

VPN and Pi-hole are independent — see §8.

### LAN-only traffic (no Obscura)

Traffic between devices on `192.168.1.0/24` (e.g., camera → PiSensors, client → NAS) stays local. The gateway must allow **LAN-to-LAN forwarding** without sending those packets through Obscura.

```
Phone  →  Nighthawk (switch)  →  homeassistant (.38)     ← stays on LAN, no Pi/Obscura
```

---

### 4.1 Data flow — through the Nighthawk (detailed)

This section answers: *“Does traffic go from each device on the Nighthawk, through the Pi gateway, back to the Nighthawk, then out to the internet?”*

**Yes — for VPN-routed devices after DHCP cutover.** That is normal and correct for this design.

```
Your device  →  Nighthawk (WiFi/switch)  →  Pi gateway  →  Nighthawk (WAN uplink)  →  modem  →  Obscura  →  website
```

The Nighthawk is not “routing twice” in a loop. It plays **two different roles** on that path.

#### Single network jack on the Pi

The gateway Pi has **one Ethernet cable** to the Nighthawk (or a switch). It sits **on the LAN** like any other device — not between the modem and the router. No USB Ethernet adapter is required. Outbound VPN traffic uses the virtual **`wg0`** interface; packets still leave the house on the same physical **`eth0`** cable.

#### What the Nighthawk does after cutover

Everything — phones, TVs, the Pi — shares the same LAN (`192.168.1.0/24`).

| Role | What the R8000 does |
|------|---------------------|
| **WiFi access point** | Phones and laptops connect wirelessly |
| **Ethernet switch** | Forwards frames between LAN ports (including to the Pi) |
| **WAN router** | Sends traffic to the modem **only when something needs the public internet** (Pi → Obscura, bypass devices, etc.) |

After cutover, **clients no longer use `.1` as their default gateway.** DHCP from the Pi hands out `GATEWAY_IP` instead. The Nighthawk stops being the default router for everyday devices — it mostly **switches** traffic to and from the Pi.

#### Step by step: phone on WiFi opens a website

**Hop 1 — Phone → Pi (first Nighthawk crossing)**

The phone’s default gateway is the Pi (e.g. `.100`), not the Nighthawk (`.1`).

```
Phone  --WiFi-->  Nighthawk  (switch: “destination MAC is the Pi”)
                      |
                 Pi eth0  (GATEWAY_IP)
```

The Nighthawk forwards at Layer 2 — it is **not** choosing the internet path for the phone.

**Hop 2 — Pi → Obscura (second Nighthawk crossing)**

The Pi encrypts the traffic into WireGuard (`wg0`) and sends the tunnel packet to the Obscura server on the public internet. That packet goes out the **same Ethernet cable** through the Nighthawk’s WAN port:

```
Pi  --eth0-->  Nighthawk  --WAN-->  modem  -->  Obscura server
                                              |
                                         decrypts & forwards
                                              |
                                           website
```

**Return path**

```
website  →  Obscura  →  modem  →  Nighthawk  →  Pi  →  Nighthawk  →  phone
```

#### Physical topology

```
                    ┌─────────────────────────────────┐
                    │     Nighthawk R8000 (.1)        │
  Phone ──WiFi──────│  WiFi + switch + WAN to modem   │
                    │         │              │        │
                    │    LAN ports      WAN port      │
                    └───┬───────────────┬───┴──────────┘
                        │               │
                   Pi gateway      ISP modem
                   (GATEWAY_IP)         │
                        │               │
                   wg0 ────────────────┘  (encrypted tunnel to Obscura)
                        └──► Obscura ──► Internet
```

Same physical box, two jobs: **switch** (device ↔ Pi) and **router** (Pi ↔ internet).

#### Before vs after

| | Path |
|---|------|
| **Today (no gateway)** | `Phone → Nighthawk (.1) → modem → ISP → website` |
| **With gateway** | `Phone → Nighthawk (switch) → Pi → Nighthawk (WAN) → Obscura → website` |

The Pi adds one logical hop plus encryption — the expected tradeoff for whole-house VPN.

#### Bypass devices (direct WAN)

Work laptops, PiFirewall (`.28`), and other bypass hosts still use the Pi as their **default gateway** (from DHCP), but **policy routing on the Pi** sends their internet traffic out via the R8000 (`.1`) — not through Obscura:

```
Work laptop  →  Nighthawk  →  Pi (bypass policy)  →  Nighthawk WAN  →  modem  →  ISP
```

They appear on the public internet with your **home IP**, not an Obscura exit IP.

#### What this design is *not*

| Misconception | Reality |
|---------------|---------|
| Pi sits between modem and router | Pi is **on the LAN**; one cable to the Nighthawk |
| Traffic loops through the Nighthawk endlessly | Straight path: device → Pi → WAN via Obscura |
| Nighthawk still routes all client internet traffic | Clients use **Pi as gateway**; Nighthawk **switches** to the Pi |
| Two network jacks required | **One** built-in Ethernet port is enough |

---

## 5. Gateway IP address

**Decision deferred.** A specific IP is not assigned yet.

- The original draft guide used `192.168.1.5`, but that address is already in use on the network by an unknown WiFi client.
- A high address such as **`192.168.1.100`** is a reasonable candidate — easy to remember, unlikely to collide with existing Pis and IoT devices.
- Final IP will be chosen during pre-build inventory and reserved on the router (or only in dnsmasq after cutover).

Throughout this document, **`GATEWAY_IP`** means “whatever IP we assign the gateway Pi.” Replace it in configs at build time.

---

## 6. Devices and roles (current understanding)

This is a **working list** based on inventory and router data from June 2026. It will be refreshed before build.

### Core infrastructure

| Device | IP | Role | Notes |
|--------|-----|------|--------|
| R8000 router | `.1` | WiFi, switching, WAN uplink, port forward | N/A |
| PiHole-Main | `.4` | DNS (when opted in) | Not special-cased at cutover |
| RocPiHole | `.11` | DNS (when opted in) | Primary Pi-hole when opted in |
| Prometheus / renegade | `.16` | Monitoring, Grafana, Ansible | Opt in via UI when ready |
| PiSensors | `.26` | IoT hub, **management UI** | Opt in via UI when ready |
| PiFirewall | `.28` | Offsite backup WireGuard tunnel | **Always bypass VPN** — hardcoded exception |
| homeassistant | `.38` | Home Automation | Opt in via UI when ready |
| Pi5Desktop | `.42` | Dev desktop (existing Pi 5, 8 GB) | Opt in via UI when ready |
| piImmich | `.63` | Photo management | Opt in via UI when ready |
| **Gateway Pi (new)** | `GATEWAY_IP` | Obscura client, inbound VPN, DHCP, NAT | N/A — it *is* the gateway |

### Policy at cutover (no mass conversion)

When DHCP moves to the gateway Pi:

1. **Import reservations** from the R8000 (IP, MAC, hostname) — same addresses as today.
2. **Do not** enable VPN or Pi-hole on any device automatically.
3. Every imported device starts as **`vpn_mode: bypass`** and **`use_pihole: false`** unless you change it in the UI.
4. **Exception:** PiFirewall (`.28`) is always **`vpn_mode: bypass`** — cannot be toggled to routed (breaks its own tunnel).

You then **opt in** VPN and/or Pi-hole per device while testing — e.g. enable Obscura on your phone first, Pi-hole on a second device, etc.

### Hardcoded exceptions (always bypass VPN)

| Device | IP | Reason |
|--------|-----|--------|
| PiFirewall | `.28` | Runs its own outbound WireGuard backup tunnel |

Work laptops that need direct WAN will simply stay on the default (bypass) until you deliberately opt them into Obscura — which you would not do for corporate VPN machines.

### LAN-only / low-WAN devices

Cameras, Pico W sensors, PiMatrix, piClock, RaspiNAS, and similar devices primarily talk on the LAN. They still receive a default gateway via DHCP but their important traffic is local.

---

## 7. DHCP migration strategy

**Principle: preserve what Netgear already does.**

### Address layout (`192.168.1.0/24`)

| Range | Role |
|-------|------|
| **`.1`** | R8000 router (static; not in DHCP pool) |
| **`.2`–`.254`** | Reservations allowed anywhere on the subnet (homelab gear mostly **`.2`–`.100`** today) |
| **`.101`–`.254`** | **Dynamic pool** — new / guest / unreserved devices |
| **`.255`** | Broadcast (never assigned) |

**`GATEWAY_IP`** (e.g. **`.100`**) is a **reservation** for the gateway Pi’s MAC, not part of the dynamic pool.

**Reserved IPs are never handed out dynamically.** dnsmasq `dhcp-host` entries take precedence; the API must not assign the same IP to two MACs. A reservation may sit inside **`.101`–`.254`** (e.g. a Pi at **`.150`**) — that IP is excluded from the pool for everyone else.

Example dnsmasq pool line:

```
dhcp-range=192.168.1.101,192.168.1.254,255.255.255.0,4h
```

### Reserved vs dynamic (sticky) leases

| Type | dnsmasq | UI label | Behavior |
|------|---------|----------|----------|
| **Reserved** | `dhcp-host=MAC,name,IP` | **Rsvd** | Always gets that IP. Survives reboots and lease expiry. |
| **Dynamic** | Address from pool only | **Dyn** | No `dhcp-host` row yet. |

**Sticky dynamic leases:** For devices without a reservation, dnsmasq **re-offers the same IP** to the same MAC on renew/reconnect **when that IP is still available** — i.e. it is not reserved for another MAC and not actively leased to a different MAC. This gives phones and laptops a **familiar IP** without admin action.

Sticky is **not** a guarantee: if the IP was taken by another device, the lease expired and the address was recycled, or you add a reservation for someone else at that IP, the client gets the next free pool address on the next DHCP cycle.

| Lease length | Applies to | Rationale |
|--------------|------------|-----------|
| **4 hours** | Dynamic pool (`.101`–`.254`) | Guests release addresses; unknown devices reappear in the UI sooner |
| **24 hours** | Reserved (`dhcp-host`) | Stable; IP is fixed by reservation anyway |

Pool bounds and lease lengths are **configurable on the Settings page** (§8.1); saving regenerates the dnsmasq `dhcp-range` line and reloads dnsmasq.

### Promote dynamic → reserved (UI)

From a **Dyn** device row or detail view:

1. **Reserve…** — MAC pre-filled; IP defaults to **current lease** (keep familiar address) or admin picks **any unused IP** on the subnet.
2. API validates: IP not in use, not router (`.1`), not gateway unless that device *is* the gateway.
3. Save → `dhcp-host` + registry entry; device keeps or moves to chosen IP on **next DHCP renew**.

**`+ Add device`** creates a reservation directly (no prior lease required).

When the gateway Pi is ready:

1. Export the R8000’s **Attached Devices** and **Address Reservations** (fresh copy at cutover time).
2. Build a **dnsmasq** config on the Pi that includes:
   - `dhcp-option=3,GATEWAY_IP` — hand out the Pi as default gateway (all devices — required for routing)
   - **Default DNS for dynamic pool:** public resolvers (`1.1.1.1`, `8.8.8.8`) — see §8 global defaults
   - **Per-device overrides** from the device registry (Pi-hole tag, reservations) — generated by the API
   - Every **`dhcp-host=MAC,name,IP`** reservation from the R8000 (addresses preserved; policy flags start as bypass / no Pi-hole)
   - Dynamic pool **`192.168.1.101`–`192.168.1.254`** (4h lease; sticky via dnsmasq lease file)
3. Test dnsmasq on the Pi **before** disabling R8000 DHCP.
4. Disable R8000 DHCP only after confirming the Pi is issuing leases correctly.
5. Force DHCP renewal on key devices (desktop, phones) and verify gateway + DNS.

### Rules for the reservation list

- **One MAC → one IP.** No duplicate entries.
- **Do not include** decommissioned devices (e.g., old NEMS at `.43`, inactive Pi5Filter at `.32`) unless they come back online.
- **Add** the gateway Pi’s MAC → `GATEWAY_IP` → `pivpngateway`.
- Fix known stale names (e.g., `.63` should be `piImmich`, not `pi5Desktop`).

### Safe dnsmasq testing (before R8000 DHCP is disabled)

**Do not run two DHCP servers on the LAN for normal clients.** If both the R8000 and dnsmasq answer DISCOVER packets, devices get random leases from whichever server responds first — broken gateways, duplicate IPs, and hard-to-debug cutover failures.

Use one of these approaches **in order of preference**:

| Method | When to use | Steps |
|--------|-------------|--------|
| **A. Isolated test client (recommended)** | Before any house-wide change | One **wired** machine (e.g. Pi5Desktop `.42`). Temporarily set **static** IPv4: address in `.101`–`.254`, mask `/24`, gateway = `GATEWAY_IP`, DNS = `1.1.1.1`. Leave R8000 DHCP **on** for everyone else. Start dnsmasq on the gateway with a **narrow test pool** (e.g. `.200`–`.210`) or rely on static-only test — confirm the Pi does **not** bind `0.0.0.0:67` in a way that steals leases from `.1` (see dnsmasq `interface=` / `bind-interfaces` below). Run `dhclient -v eth0` or renew on the test host only. |
| **B. Maintenance-window cutover** | After Method A passes | Short window: disable R8000 DHCP → enable full dnsmasq → renew key devices. Wired path to router admin required (§14). |
| **C. dnsmasq bind to gateway IP only** | During Method A | In dnsmasq: `interface=eth0`, `bind-interfaces`, and ensure only the test pool is offered. Avoid `dhcp-range` spanning addresses the R8000 is still leasing. |

**dnsmasq listen safety while R8000 still runs DHCP:**

```
interface=eth0
bind-interfaces
# Optional during test: no dhcp-range until ready, or a tiny range outside active R8000 leases
```

Verify with `sudo ss -ulnp | grep 67` — only one server should answer on the LAN during production; during Method A, ideally only the R8000 until you intentionally test one client.

**Pre-cutover test checklist (one client):**

1. Gateway Pi static IP (`GATEWAY_IP`) reachable from test client.
2. Test client uses `GATEWAY_IP` as default gateway (static or Pi-issued lease).
3. `dig @1.1.1.1` works; opt-in Pi-hole test: add one `dhcp-host` + `set:pihole`, renew, confirm DNS = `.11`/`.4`.
4. Bypass routing: client shows home ISP public IP (`curl -s https://ifconfig.me`); [Obscura check connection](https://obscura.com/) not connected.
5. Routed test: add reservation + `ip rule` for that IP (§12.1); public IP ≠ home ISP; Obscura check page shows connected.

Only after 1–5 pass: disable R8000 DHCP and expand to full reservation list (Phase 4).

### Cutover posture — gateway stays on, repoint at your pace

**Plan:** Disable DHCP on the R8000 once gateway dnsmasq is verified. The gateway Pi stays up as DHCP server and default router for the LAN.

**Deliberately relaxed about client timing:**

- Devices that **renew DHCP** pick up `GATEWAY_IP` and public DNS automatically — no rush to touch every phone/tablet.
- Devices with **static IP or hardcoded gateway** (`192.168.1.1`) keep old behavior until you repoint them manually — fix when you are ready, not in one maintenance blitz.
- **Stale DHCP cache** on a slow device is acceptable; the house keeps working for bypass-default policy while you migrate device-by-device.

**When you repoint a device**, verify: gateway = `GATEWAY_IP`, DNS matches policy (public vs Pi-hole), then opt into VPN/Pi-hole in the UI when testing that device.

Casting / mDNS issues (Chromecast, AirPlay, some TVs): address **if and when** a specific device breaks — same gradual approach.

---

## 8. Device policy — opt-in VPN and Pi-hole

VPN routing and Pi-hole DNS are **independent toggles**, both **off by default**. You enable each per device (or via global defaults for new joiners) when you are ready to test.

### 8.1 Global defaults (new devices)

Configurable in the management UI **Settings** page. Stored on the gateway as `network_defaults`:

**Infrastructure (read-only in UI)**

| Field | Example | Notes |
|-------|---------|--------|
| **`gateway_ip`** | `GATEWAY_IP` | This Pi — shown for reference; changed via OS/network config, not Settings |
| **`subnet`** | `192.168.1.0/24` | Read-only |

**New device policy**

| Setting | Initial value | Meaning |
|---------|---------------|---------|
| **`default_vpn_mode`** | `bypass` | New / unregistered devices use direct WAN (home IP), not Obscura |
| **`default_use_pihole`** | `false` | New / unregistered devices get public DNS, not Pi-hole |

**DNS & routing**

| Setting | Initial value | Meaning |
|---------|---------------|---------|
| **`default_public_dns_primary`** | `1.1.1.1` | Public DNS for clients **not** on Pi-hole (primary slot) |
| **`default_public_dns_secondary`** | `8.8.8.8` | Public DNS secondary; **optional** if primary is set |
| **`pihole_dns_primary`** | `192.168.1.11` | Pi-hole for opted-in clients and PiVPN profiles (primary slot) |
| **`pihole_dns_secondary`** | `192.168.1.4` | Pi-hole secondary; **optional** if primary is set |
| **`wan_gateway_ip`** | `192.168.1.1` | **Required.** Upstream router (R8000) — bypass next-hop for policy routing table `100` |

**Validation (API on `PUT /api/settings`):**

| Rule | Behavior |
|------|----------|
| **`wan_gateway_ip`** | **Mandatory.** Non-empty valid IPv4 on the LAN subnet. Must not equal `gateway_ip`. Save rejected if missing. |
| **Public DNS pair** | At least **one** of `default_public_dns_primary` or `default_public_dns_secondary` must be set. |
| **Pi-hole DNS pair** | At least **one** of `pihole_dns_primary` or `pihole_dns_secondary` must be set. |
| **Single resolver** | If only one of a pair is set, dnsmasq / PiVPN push **that IP only** — all DNS for that mode uses the single server. |
| **Empty secondary** | Blank secondary is allowed; omitted from generated `dhcp-option=6` and Pi-hole tag lines. |

Saving DNS/routing settings regenerates dnsmasq, **`apply-policy-routing.sh`** (uses `wan_gateway_ip`), and reloads. Document in UI: changing `wan_gateway_ip` affects every bypass device immediately.

**DHCP (dnsmasq)**

| Setting | Initial value | Meaning |
|---------|---------------|---------|
| **`dhcp_pool_start`** | `192.168.1.101` | First address in the dynamic pool |
| **`dhcp_pool_end`** | `192.168.1.254` | Last address in the dynamic pool |
| **`dhcp_dynamic_lease_hours`** | `4` | Lease time for **dynamic** (unreserved) clients |
| **`dhcp_reserved_lease_hours`** | `24` | Lease time on **`dhcp-host`** reservation lines |

**Sticky dynamic** (same MAC re-offered the same pool IP when free) is always enabled — standard dnsmasq lease-file behavior; not a separate toggle.

Saving DHCP settings validates that the pool does not include `.1` (router) or `GATEWAY_IP`, then regenerates dnsmasq and reloads. Reserved IPs anywhere on the subnet remain excluded from the pool regardless of range.

**Applies to:**

| Device type | Behavior |
|-------------|----------|
| **Unknown device** (first DHCP lease, not in registry) | Gets global defaults; appears in UI as “new — click to manage” |
| **Imported reservation** (from R8000 at cutover) | Starts as bypass + no Pi-hole; same as global defaults initially |
| **Manually added reservation** | Pre-filled from global defaults; you can override before saving |

Change global defaults anytime — affects **only devices** that have not been explicitly configured (or add a “reset unconfigured to defaults” action in v1.1).

**Example rollout:** Leave defaults as-is → cut over DHCP → everything works like today → opt your phone into VPN → test → opt into Pi-hole on one device → test → expand.

### 8.2 Per-device toggles (UI)

Each device in the registry has two independent fields:

| Field | `false` / bypass (default) | `true` / routed or Pi-hole |
|-------|---------------------------|----------------------------|
| **`vpn_mode`** | `bypass` — direct WAN via ISP (home IP) | `routed` — internet via Obscura |
| **`use_pihole`** | Public DNS (from `default_public_dns_*`) | Pi-hole (from `pihole_dns_*`) |

**Combinations:**

| VPN | Pi-hole | Typical use |
|-----|---------|-------------|
| Off | Off | Default at cutover; behaves like pre-gateway |
| On | Off | Private browsing; ads still show (family member) |
| Off | On | Ad blocking only; home IP visible to sites |
| On | On | Full privacy + ad blocking |

**Apply:** Device must **renew DHCP lease** after DNS changes. VPN routing changes apply on the gateway immediately (no lease renewal needed).

**VPN routing requires a reservation (v1):** `vpn_mode: routed` is allowed only when the device has a fixed **`dhcp-host`** reservation (`reserved: true`). Dynamic (**Dyn**) devices stay **`bypass`** — their pool IP can change, and VPN policy is keyed by source IP (§10.4). Pi-hole opt-in works on dynamic devices (dnsmasq keys DNS by MAC). In the UI, the VPN toggle is disabled on dynamic rows until you **Reserve…** the device.

**Hard lock:** PiFirewall (`.28`) — `vpn_mode` always `bypass`; UI shows toggle disabled with explanation.

### 8.3 dnsmasq generation (from registry + defaults)

Values below come from `network_defaults` (§8.1). Omitted secondaries when blank.

```
# Gateway for everyone (required)
dhcp-option=3,GATEWAY_IP

# Default DNS — public (both set)
dhcp-option=6,1.1.1.1,8.8.8.8

# Default DNS — public (primary only)
# dhcp-option=6,1.1.1.1

# Pi-hole opt-in tag (both set)
dhcp-option=tag:pihole,option:dns-server,192.168.1.11,192.168.1.4

# Pi-hole opt-in tag (primary only)
# dhcp-option=tag:pihole,option:dns-server,192.168.1.11

# Device opted into Pi-hole
dhcp-host=AA:BB:CC:DD:EE:FF,MyPhone,192.168.1.50,set:pihole

# Device not using Pi-hole — plain dhcp-host line, no tag
dhcp-host=54:6C:EB:...,ChristinaComp,192.168.1.20
```

VPN mode is **not** set in dnsmasq — it is enforced via gateway policy routing (§10.4) using **`wan_gateway_ip`** for bypass.

### 8.4 Pi-hole upstream and redundancy (for opted-in devices)

Configure both Pi-holes with privacy-oriented upstream DNS. If your Obscura WireGuard config includes a `DNS` entry, you may use that resolver for Pi-hole upstream; otherwise use DoH/DoT to a trusted resolver (e.g. Cloudflare `1.1.1.1`, Quad9). Do **not** hardcode Mullvad DNS IPs — they are not part of an Obscura subscription.

**Dual DNS (v1):** Clients receive whichever Pi-hole IPs are configured in Settings (`pihole_dns_primary` / `pihole_dns_secondary`) — one or two. Same for public DNS. No health-aware dnsmasq switching in v1.

**Monitoring:** Existing **Grafana** (`.16`) alerts when a Pi-hole is down — that is sufficient for v1. No automatic removal of a failed resolver from DHCP options.

### 8.5 Device identity: WiFi vs home access VPN

One phone or laptop can appear in the UI as **a single device**, but it connects in **two different ways**. Policy differs by connection type.

#### Summary (confirmed design)

| Connection | Obscura (commercial VPN) | Pi-hole |
|------------|--------------------------|---------|
| **Home WiFi / LAN** | Per device setting in UI (opt-in; default **off**) | Per device setting in UI (opt-in; default **off**) |
| **Home access VPN** (inbound WireGuard, remote) | **Always on** while tunnel is connected | **Always on** while tunnel is connected |

**Example:** A family member’s phone on WiFi can have VPN off and Pi-hole off (ads OK at home). The same phone connected to **home access VPN** from outside always gets **Obscura + Pi-hole** — regardless of WiFi toggles.

#### At home on WiFi (LAN)

```
Phone  →  WiFi  →  DHCP: 192.168.1.x + gateway GATEWAY_IP
         →  VPN rules keyed by reserved LAN IP; Pi-hole keyed by MAC (dnsmasq)
```

| Control | Mechanism | Takes effect |
|---------|-----------|--------------|
| **VPN (Obscura)** | Gateway policy routing for that LAN IP | **Immediately** (new connections) |
| **Pi-hole** | dnsmasq DHCP DNS option | After **DHCP lease renew** |

WiFi toggles in the UI apply **only while the device is on the LAN** using its `192.168.1.x` address.

#### Away from home on home access VPN (inbound WireGuard)

```
Phone  →  cellular / other WiFi  →  tunnel to gateway (wg1)
         →  client IP e.g. 10.66.66.x (NOT 192.168.1.x)
         →  internet: wg1 → gateway → Obscura (wg0)
         →  DNS: Pi-hole (.11 / .4) pushed in WireGuard client profile
```

| Traffic | Path | Policy |
|---------|------|--------|
| **Home LAN** (`192.168.1.0/24`) | Through tunnel → gateway → LAN | Always allowed |
| **Internet** | Through tunnel → gateway → **Obscura** | **Always Obscura** — not controlled by WiFi VPN toggle |
| **DNS** | WireGuard profile → **Pi-hole** | **Always Pi-hole** — not controlled by WiFi Pi-hole toggle |

WiFi per-device toggles **do not apply** while the client is connected remotely. Remote access is **always fully protected** (Obscura + Pi-hole) by design.

#### One device record in the UI

Link both identities to the same registry entry:

```
Device: "Sarah's iPhone"
  lan_ip:      192.168.1.50
  mac:         aa:bb:cc:...
  wg_peer_ip:  10.66.66.2          # fixed per client in WireGuard config
  wg_public_key: ...

  # WiFi policy (UI toggles)
  vpn_mode:    bypass | routed
  use_pihole:  true | false

  # Remote policy (fixed — not toggled in UI v1)
  remote_always_vpn:  true
  remote_always_pihole:   true
```

When creating or updating a **home access VPN** client profile (via **PiVPN on the gateway** — §9.2), each profile must include:

- `AllowedIPs` includes `192.168.1.0/24` and `0.0.0.0/0`
- `DNS` = configured **`pihole_dns_*`** values (one or both — same rules as §8.1)

Optionally copy `wg_peer_ip` / `wg_public_key` into the device registry by hand so the UI can show tunnel identity — not automated in v1.

#### What the user experiences

| Scenario | Obscura check | Ads / Pi-hole |
|----------|---------------|---------------|
| Phone on home WiFi, both toggles **off** | Home IP | Ads visible |
| Phone on home WiFi, both toggles **on** | Obscura | Blocked |
| Same phone on **home access VPN** from outside | **Obscura** | **Blocked** |

#### Edge case: home WiFi + home access VPN simultaneously

Avoid running the home access VPN client while on home WiFi — unnecessary double tunnel. Document as best practice; not required for v1.

#### Implementation notes (build)

- **WiFi policy:** `dnsmasq` + gateway `ip rule` / iptables (§8.3, §10.4).
- **Remote policy:** WireGuard server `wg1` routing (§10.3) + DNS/`AllowedIPs` in each PiVPN client export (§9.2).
- UI shows both LAN IP and tunnel IP on device detail; WiFi toggles labeled **“Applies on home WiFi only.”**

### 8.6 Verification

Use the [Obscura check connection](https://obscura.com/) page and/or compare public IP:

```bash
curl -s https://ifconfig.me    # bypass → home ISP IP; routed → Obscura exit IP
sudo wg show wg0               # gateway: recent handshake when tunnel up
```

| Device state | Expected |
|--------------|----------|
| Default on WiFi (both off) | Home ISP public IP; Obscura check not connected; ads visible; gateway = `GATEWAY_IP` |
| WiFi: VPN on | Public IP ≠ home ISP; Obscura check connected |
| WiFi: Pi-hole on | DNS = `.11`/`.4`; ad blocking works (after DHCP renew) |
| WiFi: both on | Obscura + ad blocking |
| **Home access VPN (remote)** | **Obscura connected + Pi-hole DNS + ads blocked** — always, independent of WiFi toggles |
| WiFi off / remote on: same phone | Remote policy wins while tunnel connected |

On the gateway Pi’s Obscura config, **remove or comment out** the `DNS = …` line — DHCP handles client DNS.

---

## 9. VPN services (gateway Pi runs both)

The gateway Pi handles **two separate WireGuard roles** on different interfaces.

### 9.1 Outbound — Obscura (whole-house privacy)

**Provider:** [Obscura](https://obscura.com/) — US-payment-friendly WireGuard VPN. Obscura relays traffic to an independent Mullvad exit hop; the gateway runs **one WireGuard client config** from the Obscura portal (no separate Mullvad account).

| Setting | Value |
|---------|-------|
| Role | WireGuard **client** to Obscura |
| Config file | `/etc/wireguard/obscura.conf` |
| Interface | `wg0` |
| AllowedIPs | `0.0.0.0/0` (all IPv4 through tunnel) |
| Autostart | `systemctl enable wg-quick@obscura` |

**Obscura portal setup:**

1. Sign in at [obscura.com](https://obscura.com/) → **Manage WireGuard configs** → **Register WireGuard config**.
2. Choose **relay server** (and optional **exit server**) location → **Generate config**.
3. Copy the config to the gateway as `/etc/wireguard/obscura.conf`.
4. **Remove or comment out** any `DNS = …` line — DHCP handles client DNS (§8).

See [Using Obscura via WireGuard](https://obscura.com/knowledge/using-obscura-via-wireguard/) for portal steps.

**Account limit:** Obscura allows **3 WireGuard config slots** per account (each registered config reserves one slot even when disconnected). The gateway uses **1 slot** — plan remaining slots for any personal devices that also use Obscura WireGuard configs (vs the native Obscura app, which shares the same 3 concurrent connection limit differently).

**Note:** WireGuard compatibility mode uses standard WireGuard (not QUIC obfuscation). That is fine for a fixed home gateway on Linux.

All LAN internet traffic and inbound-VPN-client internet traffic exits through this tunnel.

### 9.2 Inbound — home access (phone / laptop remote)

| Setting | Value |
|---------|-------|
| Role | WireGuard **server** — replaces PiHole-Main (`.4`) as the home VPN endpoint |
| Server | **`wg1`** via WireGuard / `wg-quick@home` (or PiVPN-managed equivalent on gateway) |
| Config file | `/etc/wireguard/home.conf` (name TBD at build) |
| Interface | `wg1` |
| Listen port | `51820/udp` |
| Client subnet | Private range, e.g. `10.66.66.0/24` (exact range TBD at build) |
| Autostart | `systemctl enable wg-quick@home` |
| **Client profiles (v1)** | **PiVPN on the gateway Pi** — add/revoke phone and laptop configs manually (`pivpn add`, etc.). **Not** exposed via management API/UI in v1. |

**Router port forward:** R8000 forwards **UDP 51820** from WAN → `GATEWAY_IP:51820`. Update from the current forward to PiHole-Main (`.4`) during cutover.

**Client routing (what phone/laptop config must do):**

| Destination | Route |
|-------------|--------|
| `192.168.1.0/24` | Through inbound tunnel → gateway forwards to LAN |
| `0.0.0.0/0` (internet) | Through inbound tunnel → gateway → Obscura `wg0` |

Create client configs on the gateway during Phase 2 with **PiVPN**, ensuring each profile matches §8.5 (`AllowedIPs`, Pi-hole DNS). Management UI may display `wg_peer_ip` if you enter it in the registry manually — it does not generate or rotate WireGuard configs.

### 9.3 Decommission inbound VPN on PiHole-Main

After inbound VPN is verified on the gateway:

1. Stop and disable the WireGuard **server** on PiHole-Main (`.4`).
2. Remove the old R8000 port forward to `.4` (replaced by forward to `GATEWAY_IP`).
3. PiHole-Main continues as DNS only — no VPN bypass rules needed on the gateway for `.4`.

---

## 10. Routing and firewall (critical design)

This section captures lessons from the draft guide review. These are **requirements**, not optional nice-to-haves.

### 10.1 IP forwarding

The Pi must forward packets between `eth0` (LAN), `wg0` (Obscura), and `wg1` (inbound VPN clients):

```
net.ipv4.ip_forward=1
```

### 10.2 Basic iptables roles

| Rule set | Purpose |
|----------|---------|
| **FORWARD** `eth0 → wg0` | Allow LAN clients out through Obscura |
| **FORWARD** `wg1 → wg0` | Allow inbound VPN client internet traffic out through Obscura |
| **FORWARD** `wg1 → eth0` | Allow inbound VPN clients to reach LAN |
| **FORWARD** `wg0 → eth0` (established) | Allow return traffic |
| **NAT MASQUERADE** on `wg0` | Make LAN and inbound-client traffic appear as Obscura IP |
| **FORWARD** LAN-to-LAN | Allow `192.168.1.0/24 ↔ 192.168.1.0/24` without Obscura |
| **Default FORWARD policy DROP** | Kill switch for **routed** devices when Obscura is down — bypass devices still exit via WAN |

### 10.3 Inbound VPN client routing (on gateway)

Traffic from remote clients arrives on `wg1`. The gateway splits it:

| Source | Destination | Action |
|--------|-------------|--------|
| `wg1` (client subnet) | `192.168.1.0/24` | Forward to LAN (`eth0`) — no Obscura |
| `wg1` (client subnet) | internet | Forward to `wg0` → Obscura |
| `wg1` | Pi-hole DNS (`.4`, `.11`) | Forward to LAN — clients use Pi-hole through tunnel |

Use iptables FORWARD rules and NAT (MASQUERADE on `wg0` for internet-bound client traffic). Ensure return routes for the client subnet are on the gateway (`wg1`).

Remote clients must **not** be affected by bypass rules meant for PiFirewall or work laptops.

### 10.4 VPN routing — opt-in policy routing

**Default: bypass (direct WAN).** Most devices behave like today until you opt them into Obscura in the UI.

| `vpn_mode` | Routing |
|------------|---------|
| **`bypass`** (default) | Gateway policy-routes traffic to **`wan_gateway_ip`** (R8000) → ISP — home public IP |
| **`routed`** (opt-in) | Traffic exits via `wg0` → Obscura |

#### v1 decision: reservation required for `routed`

VPN policy uses **`ip rule … from <LAN IP>`** (see below). If a device’s IP changes (dynamic pool, sticky lease lost), rules point at the wrong host or stop matching.

| Approach | Verdict for v1 |
|----------|----------------|
| **Require reservation before `routed`** | **Chosen.** Stable IP; matches registry model; simple `ip rule` generation. Dynamic devices stay bypass until **Reserve…**. |
| **MAC-based policy routing** | Possible (`iptables`/`nftables` mark by `--mac-source` in `FORWARD`) but more moving parts, harder to debug, and duplicates what reservations already solve. Revisit only if you need routed mode on intentionally unreserved pool devices. |

**Rules:**

- **`vpn_mode: routed`** — only if `reserved: true` (fixed `dhcp-host`). API/UI reject routed on dynamic devices.
- **`vpn_mode: bypass`** — all dynamic devices; all reserved devices unless explicitly routed; PiFirewall (`.28`) always bypass.

When Obscura WireGuard is up, it installs a default route through `wg0`. **Bypass** hosts need explicit direct-WAN policy so they are not pulled into the tunnel:

```
# Bypass — direct WAN (one rule per reserved bypass IP; next-hop from wan_gateway_ip)
ip rule add from 192.168.1.20 table 100
ip route add default via ${WAN_GATEWAY_IP} dev eth0 table 100

# Routed (opt-in, reserved IP only) — use main table / wg0
# (no special rule — default route via wg0 applies)
```

`${WAN_GATEWAY_IP}` is **`network_defaults.wan_gateway_ip`** (§8.1). The API generates **`ip rule` + routes** for every **bypass** reserved IP (every reserved device not explicitly `routed`, plus hardcoded `.28`). Persist across reboot (WireGuard `PostUp`/`PreDown` or systemd unit). See §10.8 for a starter script layout.

Bypass iptables rules (FORWARD accept + MASQUERADE out `eth0`) still apply; **policy routing comes first**.

### 10.5 Kill switch — routed devices only

The kill switch ensures that if WireGuard drops, **routed (opt-in) devices lose internet** rather than leaking to the ISP.

**Bypass (default) devices keep internet** when Obscura is down — they use direct WAN by design.

#### Kill switch and the UI

The kill switch is **infrastructure, not a setting**. It is **always enabled** for routed devices in v1 — there is no UI toggle to turn it off (disabling it would allow ISP leaks for opted-in devices).

| Layer | Behavior |
|-------|----------|
| **Gateway (iptables/nftables)** | Default FORWARD DROP + no WAN path to `wg0` when tunnel down (§10.2, §10.8). |
| **Management UI** | **Status only.** Header pill: **Obscura ○ Down · Kill switch active** (see `UI-MOCKS.md`). Banner on Dashboard when tunnel is down: routed devices have no internet; bypass devices still work. |
| **API** | `/api/status` exposes `vpn_up`, `kill_switch_active` (derived: tunnel down AND firewall in strict mode). |

Do not build an “disable kill switch” control in v1. If you need emergency internet for a routed device during an Obscura outage, change that device to **bypass** in the UI (or manually remove from routed list — §12.1) — that is a policy change, not a global kill-switch override.

**Do not** use a broad rule like “allow all `eth0 → eth0` forwarding.” That would let **routed** traffic leak when the tunnel is down.

**Instead:**

- Allow `eth0 → eth0` only for **LAN-to-LAN** (source and destination both in `192.168.1.0/24`).
- Allow `eth0 → wg0` for **routed** traffic only when the tunnel is up.
- Allow explicit **bypass** sources out `eth0` to WAN always (tunnel up or down).
- Default **FORWARD DROP** catches unrouted WAN attempts when `wg0` is down.
- Consider **OUTPUT** rules on the Pi itself so the gateway host cannot leak (§10.7).

### 10.6 IPv6 — disable to prevent leaks

If IPv6 is left enabled, clients may bypass the IPv4-only WireGuard tunnel entirely.

**On the gateway Pi** (`/etc/sysctl.conf`):

```
net.ipv6.conf.all.disable_ipv6 = 1
net.ipv6.conf.default.disable_ipv6 = 1
net.ipv6.conf.lo.disable_ipv6 = 1
```

**On the R8000:** Advanced → IPv6 → Disabled.

Re-check after cutover — Pi5Desktop (`.42`) currently has IPv6 addresses on the LAN; the gateway Pi must not.

### 10.7 Gateway host self-traffic

Policy routing and Obscura’s default route affect **client** traffic forwarded through the Pi. The **gateway process itself** (WireGuard handshakes, `curl` tests, NTP, API, Prometheus scrape) must not leak to the ISP when `wg0` is up, and must stay reachable on `GATEWAY_IP` when the tunnel is down.

#### Routing tables (conceptual)

| Traffic | Table | Path |
|---------|-------|------|
| **Forwarded LAN clients** | Main + table `100` per §10.4 | Bypass IPs → table 100 → `.1`; routed IPs → main → `wg0` |
| **Gateway own packets to LAN** | Main | Direct `eth0` |
| **Gateway own packets to internet** | Main (with care) | Prefer **`wg0`** when Obscura is up so admin tests match reality; or table **`200`** for management-only direct WAN if you need SSH/API reachable during Obscura outages without using the tunnel |

When `wg-quick@obscura` brings up `wg0` with `AllowedIPs = 0.0.0.0/0`, the **main table default route** often points at `wg0`. That is correct for **routed client** forwarding but can break **bypass client** traffic unless bypass `ip rule` entries exist (§10.4).

#### Gateway self-traffic checklist (Phase 3)

1. **`ip rule list` / `ip route show table all`** — after `wg0` up: every bypass reserved IP has `from X lookup 100`; table 100 default via **`wan_gateway_ip`** dev `eth0`.
2. **From the Pi shell:** `curl -s https://ifconfig.me` shows Obscura exit IP (not home ISP); `sudo wg show wg0` shows recent handshake.
3. **Obscura down (`wg-quick down obscura`):** bypass test client still reaches internet; routed test client does not; Pi still SSH-able on `GATEWAY_IP` via LAN.
4. **WireGuard endpoints:** Obscura relay IP must be reachable — add a **`/32` route via `wan_gateway_ip`** for the Obscura endpoint if the tunnel fails to handshake when default is wrong (common fix in `PostUp` of `obscura.conf`):

```
# Example PostUp (endpoint IP varies — use value from Obscura config)
PostUp = ip route add <OBSCURA_ENDPOINT_IP>/32 via <WAN_GATEWAY_IP> dev eth0
PreDown = ip route del <OBSCURA_ENDPOINT_IP>/32 via <WAN_GATEWAY_IP> dev eth0
```

5. **NTP:** install `systemd-timesyncd` or `chrony`; confirm sync before WireGuard tests.

#### OUTPUT chain (gateway host leak prevention)

In addition to FORWARD rules (§10.8), restrict **the Pi’s own** egress when `wg0` is down if the main default would otherwise exit via `eth0` for processes that should use Obscura:

- Prefer **`iptables -o wg0`** acceptance for forwarded routed traffic over changing OUTPUT broadly.
- Minimum: log or drop **forwarded** WAN leaks via FORWARD default DROP (§10.5); for OUTPUT, ensure management (SSH, API on `8080`) is explicitly allowed on `eth0` from LAN sources.

Finalize OUTPUT rules during Phase 3 burn-in — client leak prevention (FORWARD) is the priority.

### 10.8 Reference firewall ruleset (starter)

**Not copy-paste production-ready** — interface names, Obscura endpoint IP, and bypass IP list must be filled at build time. Use as the template for `PostUp`/`PreDown` in WireGuard or `/etc/pivpngateway/apply-firewall.sh` invoked by systemd.

**Variables:** `LAN=eth0`, `WG_OBSCURA=wg0`, `WG_HOME=wg1`, `WAN_GATEWAY=${wan_gateway_ip}` from registry, `LAN_NET=192.168.1.0/24`, `HOME_VPN_NET=10.66.66.0/24`.

**Policy routing (generated from registry — §12.1):**

```bash
# /etc/pivpngateway/apply-policy-routing.sh (conceptual)
ip rule add from all lookup main pref 1000
ip route flush table 100
ip route add default via ${WAN_GATEWAY} dev ${LAN} table 100
# For each reserved bypass IP (not routed):
# ip rule add from 192.168.1.20 lookup 100 pref 100
```

**iptables starter (ipv4 — adjust if using nftables backend):**

```bash
# Flush only if building fresh; in production use a managed script
iptables -P FORWARD DROP
iptables -P INPUT ACCEPT
iptables -P OUTPUT ACCEPT

# Established
iptables -A FORWARD -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# LAN ↔ LAN (no Obscura)
iptables -A FORWARD -i ${LAN} -o ${LAN} -s ${LAN_NET} -d ${LAN_NET} -j ACCEPT

# Inbound home VPN → LAN
iptables -A FORWARD -i ${WG_HOME} -o ${LAN} -d ${LAN_NET} -j ACCEPT
iptables -A FORWARD -i ${LAN} -o ${WG_HOME} -s ${LAN_NET} -d ${HOME_VPN_NET} -j ACCEPT

# Inbound home VPN → internet via Obscura
iptables -A FORWARD -i ${WG_HOME} -o ${WG_OBSCURA} -j ACCEPT

# Bypass clients → WAN via router (always — tunnel up or down)
# Repeat -s for each bypass IP, or use ipset B bypass_ips
iptables -A FORWARD -i ${LAN} -o ${LAN} -s 192.168.1.20 -d ! ${LAN_NET} -j ACCEPT
iptables -t nat -A POSTROUTING -s 192.168.1.20 -o ${LAN} -j MASQUERADE

# Routed clients → Obscura (only useful when wg0 is up)
iptables -A FORWARD -i ${LAN} -o ${WG_OBSCURA} -j ACCEPT
iptables -t nat -A POSTROUTING -o ${WG_OBSCURA} -j MASQUERADE

# Return from Obscura
iptables -A FORWARD -i ${WG_OBSCURA} -o ${LAN} -j ACCEPT

# MSS clamp (important for wg1 → wg0 double encapsulation)
iptables -t mangle -A FORWARD -p tcp -m tcp --tcp-flags SYN,RST SYN \
  -o ${WG_OBSCURA} -j TCPMSS --clamp-mss-to-pmtu
iptables -t mangle -A FORWARD -p tcp -m tcp --tcp-flags SYN,RST SYN \
  -o ${WG_HOME} -j TCPMSS --clamp-mss-to-pmtu
```

**Persistence:** `netfilter-persistent save` after Phase 3 tests, or regenerate from `/etc/pivpngateway/` scripts on every boot (preferred — stays in sync with API/registry in Phase 6).

**nftables note:** Raspberry Pi OS may use nftables backend for iptables. v1 can stay with `iptables`/`iptables-persistent` for simplicity; per-client accounting (§11.1) can move to native nftables sets later.

---

## 11. Management API and web UI (split architecture)

The gateway Pi (2 GB) stays lean: **routing, DHCP, VPN, and a thin REST API only.** The **web frontend** runs on **PiSensors** (`192.168.1.26`) — already the IoT/camera hub with an existing API on port `8000`.

> **Inventory note:** PiSensors is at **`.26`**. **`192.168.1.10`** is RaspiNAS — different host.

### Why split?

| Concern | Gateway Pi | PiSensors |
|---------|------------|-----------|
| RAM | Reserved for WireGuard + dnsmasq + firewall | Has headroom for a web UI |
| Failure impact | Must stay up for whole-house routing | UI down ≠ network down |
| Docker | Avoid on gateway | OK if already used there |

### Gateway — API layer only

Small service (Python FastAPI or similar), **no Docker**, LAN-only listen address.

**Responsibilities:**

- Read/write device registry and **`network_defaults`** (global new-device policy).
- Sync registry → `dnsmasq` reservations, **per-device DNS tags**, + reload.
- Sync VPN policy → **`ip rule` / iptables for bypass list** (every reserved device not opted into routed, plus `.28`) + persist.
- Expose live status: Obscura tunnel, inbound `wg1` peers, dnsmasq leases, interface bytes.

**Planned endpoints (v1):**

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/settings` | Global defaults (policy, DNS, `wan_gateway_ip`, DHCP pool) + read-only `gateway_ip`, `subnet` |
| `PUT` | `/api/settings` | Update defaults; validates §8.1 rules; regenerates dnsmasq + policy routing |
| `GET` | `/api/devices` | All devices (lease + policy + optional live `tx_rate`/`rx_rate` when metrics available) |
| `GET` | `/api/devices/{ip}` | Single device detail |
| `PUT` | `/api/devices/{ip}` | Update hostname, **`vpn_mode`**, **`use_pihole`**, notes, optional `wg_peer_ip` |
| `POST` | `/api/devices` | **Create** reservation, or **upsert by MAC** (Reserve… from dynamic row — same MAC updates in place) |
| `DELETE` | `/api/devices/{ip}` | **Reserved:** remove `dhcp-host` + registry row. **Dynamic:** dismiss from registry only (lease expires naturally) |
| `GET` | `/api/status` | Gateway health, tunnel up/down, uptime, `kill_switch_active` (status only — §10.5) |
| `GET` | `/api/throughput` | Aggregate interface + WireGuard rates; `?ip=` for single-device when per-client counters exist |
| `GET` | `/api/leases` | Active DHCP leases (from dnsmasq) |
| `POST` | `/api/apply` | Force regenerate configs from registry (if not auto on every change) |

**Routes (PiSensors UI):** `/gateway`, `/gateway/settings`, `/gateway/devices`, `/gateway/devices/{ip}`, `/gateway/throughput` — see `UI-MOCKS.md`.

**Device list throughput column:** Shows live ↓↑ when per-client metrics are scraping (Phase 6+); otherwise `—`. Not required before `/metrics` exists.

**Device model (each row):**

```
mac, ip, hostname, vpn_mode (routed | bypass), use_pihole (bool),
  wg_peer_ip, wg_public_key,     # optional — manual from PiVPN (§9.2); UI display only in v1
  policy_source (default | explicit), reserved (bool), notes,
  first_seen_at,                  # first DHCP request / registry create (ISO 8601)
  last_renewed_at,                # last lease grant or renew (from dnsmasq lease file)
  lease_expires_at                # current lease expiry (live; null if offline / no lease)
```

**Global settings (`network_defaults`):**

```
# Read-only (included in GET; not accepted on PUT)
gateway_ip: GATEWAY_IP
subnet: 192.168.1.0/24

# New device policy
default_vpn_mode: bypass | routed
default_use_pihole: false | true

# DNS & routing (§8.1 validation)
default_public_dns_primary: 1.1.1.1      # ≥1 of primary/secondary required
default_public_dns_secondary: 8.8.8.8    # optional
pihole_dns_primary: 192.168.1.11         # ≥1 of primary/secondary required
pihole_dns_secondary: 192.168.1.4        # optional
wan_gateway_ip: 192.168.1.1              # required — bypass next-hop (R8000)

# DHCP
dhcp_pool_start: 192.168.1.101
dhcp_pool_end: 192.168.1.254
dhcp_dynamic_lease_hours: 4
dhcp_reserved_lease_hours: 24
```

**Apply flow — save Settings:**

1. UI `PUT`s `/api/settings` with changed fields.
2. API validates §8.1 rules (`wan_gateway_ip` required, ≥1 DNS per pair, etc.).
3. API writes `network_defaults`; runs `generate-dnsmasq.sh` + `apply-policy-routing.sh`.
4. UI confirms; prompt DHCP renew on devices if public or Pi-hole DNS changed.

**Apply flow — opt in to Obscura for a device:**

1. UI `PUT`s device with `vpn_mode: routed`.
2. API rejects if `reserved: false` — prompt **Reserve…** first.
3. API marks `policy_source: explicit`.
4. API removes that IP from bypass policy list; traffic uses `wg0`.
5. UI shows success; suggest Obscura check connection test (https://obscura.com/).

**Apply flow — opt in to Pi-hole for a device:**

1. UI `PUT`s device with `use_pihole: true`.
2. API adds `set:pihole` to dnsmasq `dhcp-host` line; reloads dnsmasq.
3. UI prompts user to renew DHCP lease on that device.

**Apply flow — new unknown device appears:**

1. Device gets a **dynamic** DHCP lease from **`.101`–`.254`** with global defaults (bypass, public DNS).
2. dnsmasq **re-offers the same IP** on later renews while that address remains free (sticky dynamic).
3. UI lists it under “New devices” with defaults applied.
4. Admin assigns hostname, optionally **Reserve…** (keep current IP or pick another free address), optionally opts into VPN and/or Pi-hole.

**Security:**

- **Gateway API:** Bind to `192.168.1.0/24` only — not WAN, not inbound VPN clients unless explicitly desired later. Shared secret / API token in `Authorization` header; **PiSensors backend proxies** all browser calls (token never in the browser).
- **PiSensors UI:** **HTTP basic password** (or equivalent simple login) on the web app — sufficient for home LAN admin. Session cookie after login; credentials stored in PiSensors env/config.
- Consider mutual TLS later; token + basic password is enough for v1.

### PiSensors — web frontend

Host the management website on PiSensors (`.26`). Extend the existing stack there rather than adding a second app server on the gateway.

**UI pages (v1):** See **`UI-MOCKS.md`** for wireframe sketches.

| Page | Content |
|------|---------|
| **Dashboard** | Obscura status, inbound VPN peers, aggregate throughput, dnsmasq up/down |
| **Settings** | **Global defaults** for new devices; **DNS & routing**; **DHCP pool and lease times** |
| **Devices** | All devices; VPN and Pi-hole toggles; **first seen / last renewed** timestamps; sortable |
| **Device detail** | Opt in/out: VPN, Pi-hole (**WiFi only** — §8.5); tunnel IP; notes |
| **Throughput** | Simple live charts (poll gateway API); **“View history in Grafana”** link per device |

**Frontend → API:** Browser → PiSensors (password-protected) → gateway API proxy (token server-side).

```
Browser  →  PiSensors (.26) web app  →  Gateway API (GATEWAY_IP:8080)
   ↑              ↑
 login         API token (env)
                ↓
           Grafana (.16) — historical per-device traffic (§11.1)
```

### What not to put on the gateway

- Full SPA build toolchain in production
- Docker
- Grafana / Prometheus server
- Heavy database

---

### 11.1 Per-device traffic history (Prometheus + Grafana)

Historical views — bytes sent/received **by device**, **over time**, and **by time of day** — live in **Prometheus** (store) and **Grafana** (visualize) on **renegade (`.16`)**. The PiSensors UI shows **live** stats only; Grafana is the place for history.

#### Two kinds of “by node”

| View | What it measures | Metric source |
|------|------------------|---------------|
| **Per LAN client** (phones, TVs, every DHCP IP) | Traffic that passed through the gateway | Per-IP counters **on the gateway** → Prometheus |
| **Per server** (each Pi’s own NICs) | Traffic on that machine’s interfaces | `node_exporter` on each Pi → Prometheus (already in place) |

Most “who used how much bandwidth?” questions need **per LAN client** metrics from the gateway. `node_exporter` on `.42` only tells you about `.42`’s own NIC — not what the iPhone sent through the VPN.

#### Data flow

```
LAN device (192.168.1.x)
    → Gateway Pi (counts bytes per source IP)
        → /metrics endpoint (Prometheus format)
            → Prometheus (.16) scrapes every 15–60s
                → Grafana (.16) dashboards + alerts
                    → PiSensors UI links here for history
```

#### Gateway — per-client counters

The gateway sees all routed (and bypass) traffic. Maintain byte counters **per client IP** using one of:

| Method | Notes |
|--------|--------|
| **nftables/iptables accounting** (preferred) | Rules or sets keyed by source IP; read counters periodically |
| **Custom exporter** | Same counters exposed via the gateway API `/metrics` |
| **conntrack-based** | Possible but counters are less stable across reloads |

Expose metrics in Prometheus format on the gateway (same service as the management API, or a dedicated scrape port):

```text
gateway_client_bytes_total{ip="192.168.1.42",hostname="Pi5Desktop",direction="tx",vpn="routed"} 1234567890
gateway_client_bytes_total{ip="192.168.1.42",hostname="Pi5Desktop",direction="rx",vpn="routed"} 9876543210
```

**Labels:** `ip`, `hostname`, `direction` (`tx` / `rx`), `vpn` (`routed` / `bypass`).

Gateway interface totals come from `node_exporter` on the gateway (`wg0`, `eth0`, `wg1`).

#### Prometheus (`.16`) — scrape config

Add a `gateway_api` job for `GATEWAY_IP:8080` (or dedicated metrics port) alongside existing `node_exporter` targets, including `GATEWAY_IP:9100`.

#### Grafana — example dashboards (Phase 7)

| Panel | Use |
|-------|-----|
| Traffic by device (time series) | Per-node rx/tx over time |
| Top talkers (24h / 7d) | Who used the most bandwidth |
| By time of day | Heatmap of busy hours |
| VPN vs bypass split | Obscura vs direct WAN aggregate |
| Single device drill-down | Linked from PiSensors UI via `?var-ip=…` |

Do **not** store long-term time series in the gateway API or PiSensors — that duplicates Prometheus.

#### Component roles (summary)

| Component | Live | History | Per-device |
|-----------|------|---------|------------|
| Gateway API `/api/throughput` | ✓ | ✗ | ✓ (current rates) |
| Gateway `/metrics` | ✓ (scraped) | via Prometheus | ✓ |
| `node_exporter` (each Pi) | ✓ | via Prometheus | host NIC only |
| PiSensors UI | ✓ | link only | ✓ |
| Grafana / Prometheus (`.16`) | ✓ | ✓ | ✓ |

#### Gateway capacity — A/B throughput baseline

During buildout, run the **three-path speed test** (§13) once policy routing works (Phase 3) and again after monitoring is up (Phase 5). Record results in §18 or a local build log — useful later when asking “is the Pi the bottleneck?”

---

## 12. Build phases

Execute in order. Do **not** skip ahead to DHCP cutover until WireGuard and firewall are proven on the Pi alone.

### Phase 0 — Before the Pi arrives (now)

- [ ] Identify unknown devices worth naming (especially whatever holds `.5` today).
- [ ] Fix stale R8000 names where easy (`.63` → `piImmich`).
- [ ] Choose `GATEWAY_IP` (candidate: `.100`).
- [ ] Create Obscura account; generate WireGuard config in portal (§9.1).
- [ ] Export current inbound VPN config from PiHole-Main (`.4`) for reference during migration.
- [ ] Keep this document updated.

### Phase 1 — Pi hardware and OS

- [ ] Flash **Raspberry Pi OS Lite (64-bit)** via Pi Imager.
- [ ] Set hostname: `pivpngateway`, enable SSH, **no WiFi**.
- [ ] First boot on wired Ethernet; note MAC from R8000 Attached Devices.
- [ ] Assign static IP (`GATEWAY_IP`) via **NetworkManager** (`nmcli`) — Bookworm/Trixie use NM, not dhcpcd.
- [ ] Reserve `GATEWAY_IP` for the Pi’s MAC (R8000 reservation until DHCP cutover, then dnsmasq).

### Phase 2 — WireGuard (outbound + inbound)

- [ ] Enable IPv4 forwarding.
- [ ] Disable IPv6 on Pi; disable IPv6 on R8000.
- [ ] Install WireGuard.
- [ ] **Outbound:** Install Obscura config (no `DNS` line); bring up `wg0`; verify public IP ≠ home ISP and [Obscura check connection](https://obscura.com/) shows connected; enable on boot.
- [ ] **Inbound:** Bring up home VPN server on `wg1` (port 51820); create phone/laptop client configs with **PiVPN** (§9.2).
- [ ] Update R8000 port forward: UDP `51820` → `GATEWAY_IP` (replace forward to `.4`).
- [ ] Test inbound VPN from outside WiFi: LAN reachability (e.g., `192.168.1.38:8123`) **and** Obscura check connected while browsing.

### Phase 3 — Firewall and policy routing

- [ ] Confirm LAN interface name (`eth0` vs `end0`).
- [ ] Create `/etc/pivpngateway/registry.json` and apply scripts (§12.1).
- [ ] Mount RaspiNAS backup path (§12.2); test manual registry copy.
- [ ] Install `iptables-persistent` / `netfilter-persistent`.
- [ ] Apply forwarding, NAT, LAN-to-LAN, inbound-`wg1` routing, bypass, and kill-switch rules (§10.8).
- [ ] Apply policy routing: **bypass by default**, routed list for opt-in **reserved** devices only (§10.4) — `.28` always bypass.
- [ ] Verify gateway self-traffic and Obscura endpoint route (§10.7).
- [ ] Save rules; test kill switch (Obscura down → **routed** device loses internet; **bypass** device still works).
- [ ] Test opt-in: enable VPN on one test device → public IP ≠ home ISP; others still show home ISP IP.
- [ ] **Throughput A/B test** on wired reserved test host (§13) — establish baseline before DHCP cutover.
- [ ] Re-test inbound VPN from outside WiFi after firewall rules are in place.
- [ ] Decommission inbound WireGuard server on PiHole-Main (`.4`) only after Phase 3 inbound tests pass.

### Phase 4 — dnsmasq and DHCP cutover

- [ ] Fresh export of R8000 devices and reservations.
- [ ] Install and configure dnsmasq with full reservation list + gateway option.
- [ ] **Safe test** one client per §7 (isolated static or narrow pool — **do not** dual-DHCP the house).
- [ ] Disable R8000 DHCP only after isolated test passes.
- [ ] Renew leases on key devices; confirm gateway = `GATEWAY_IP`, DNS = public (`1.1.1.1` / `8.8.8.8`) unless device is opted into Pi-hole.
- [ ] Configure Pi-hole upstream DNS (Obscura-compatible — §8.4).
- [ ] Full-house verification (Section 13).

### Phase 5 — Monitoring and cleanup

- [ ] Install `prometheus-node-exporter` on gateway Pi.
- [ ] Add `GATEWAY_IP:9100` to Prometheus on `.16`.
- [ ] Optional Grafana alerts: gateway down, tunnel stalled, dnsmasq down.
- [ ] Add Prometheus scrape job for gateway per-client metrics (Section 11.1).
- [ ] **Repeat throughput A/B test** (§13); note results in build log — compare to Phase 3 baseline.
- [ ] Update network inventory doc with final IPs and MACs.
- [ ] Label the physical Pi: hostname, IP, role.

### Phase 6 — Management API and web UI (after gateway is stable)

Build only after Phases 1–5 are verified. See Section 11.

**Gateway Pi (API only):**

- [ ] Deploy lightweight REST API (e.g. FastAPI) bound to LAN only.
- [ ] Endpoints: reservations, VPN policy per device, tunnel status, throughput.
- [ ] Expose `/metrics` (Prometheus format) with per-client byte counters (Section 11.1).
- [ ] API applies changes to dnsmasq + policy routing + iptables; validate before reload.
- [ ] Enable systemd service; document API token / auth.
- [ ] Hook registry save → NAS backup (§12.2).

**PiSensors (`.26`) — web frontend:**

- [ ] Build or extend existing web UI at `/gateway/*` to call gateway API.
- [ ] **Basic password** login on PiSensors; proxy holds gateway API token.
- [ ] Pages: device list, reservations, VPN on/off toggles, throughput dashboard.
- [ ] Link each device to Grafana dashboard for historical traffic (Section 11.1).
- [ ] Optional: link out to Grafana (`.16`) for deep metrics — don’t duplicate Grafana on the gateway.

### Phase 7 — Grafana dashboards (after per-client metrics are scraping)

- [ ] Build Grafana dashboard: traffic by device (rx/tx over time).
- [ ] Build panel: traffic by time of day (heatmap or hourly bars).
- [ ] Build panel: top talkers (24h / 7d).
- [ ] Build panel: VPN routed vs bypass totals.
- [ ] Optional alert: single device exceeds daily bandwidth threshold.

### 12.1 Pre-UI manual operations (Phases 3–5)

Phases 3–5 run **before** the management API/UI (Phase 6). Use the same files and scripts the API will later automate — do not maintain a parallel “manual only” config.

#### Registry file (source of truth until API exists)

```
/etc/pivpngateway/registry.json
```

Minimal shape (one object per device):

```json
{
  "devices": [
    {
      "mac": "54:6c:eb:aa:bb:cc",
      "ip": "192.168.1.20",
      "hostname": "ChristinaComp",
      "reserved": true,
      "vpn_mode": "bypass",
      "use_pihole": false,
      "notes": ""
    }
  ],
  "network_defaults": {
    "default_vpn_mode": "bypass",
    "default_use_pihole": false,
    "default_public_dns_primary": "1.1.1.1",
    "default_public_dns_secondary": "8.8.8.8",
    "pihole_dns_primary": "192.168.1.11",
    "pihole_dns_secondary": "192.168.1.4",
    "wan_gateway_ip": "192.168.1.1",
    "dhcp_pool_start": "192.168.1.101",
    "dhcp_pool_end": "192.168.1.254",
    "dhcp_dynamic_lease_hours": 4,
    "dhcp_reserved_lease_hours": 24
  }
}
```

Phase 6 API reads/writes this file (or equivalent SQLite) and regenerates configs on change.

#### Scripts to create in Phase 3

| Script | Purpose |
|--------|---------|
| `apply-policy-routing.sh` | Reads registry → `ip rule` / table `100` via **`wan_gateway_ip`** (§8.1) |
| `apply-firewall.sh` | iptables FORWARD/NAT/MSS from registry bypass list + static LAN/home-VPN rules (§10.8) |
| `generate-dnsmasq.sh` | Registry → `/etc/dnsmasq.d/gateway.conf` → `systemctl reload dnsmasq` |

Invoke from WireGuard `PostUp`/`PreDown` and after any manual registry edit.

#### Common manual tasks

**Opt a reserved device into Obscura (Phase 3 test):**

```bash
# 1. Ensure device is reserved in registry.json with fixed ip
# 2. Set "vpn_mode": "routed" for that entry
# 3. Regenerate policy + firewall
sudo /etc/pivpngateway/apply-policy-routing.sh
sudo /etc/pivpngateway/apply-firewall.sh
# 4. From device: curl -s https://ifconfig.me (routed → not home ISP)
```

**Revert to bypass:**

```bash
# Set "vpn_mode": "bypass"; re-run apply-policy-routing.sh
```

**Opt into Pi-hole (works on dynamic or reserved — MAC-based in dnsmasq):**

```bash
# Set "use_pihole": true; run generate-dnsmasq.sh; renew DHCP on device
```

**Add a reservation before VPN test:**

```bash
# Add dhcp-host entry via registry; generate-dnsmasq.sh; device renews DHCP
```

**Test kill switch:**

```bash
sudo wg-quick down obscura
# Routed device: no internet. Bypass device: home ISP IP on ifconfig.me, still browses.
sudo wg-quick up obscura
```

#### Phase 3 test device

Pick one **reserved** wired host (e.g. Pi5Desktop `.42` or a laptop with stable reservation). Do **not** use a dynamic pool phone as the first routed test — reserve it first if needed.

### 12.2 Registry backup (RaspiNAS)

Back up `/etc/pivpngateway/registry.json` (and optional generated configs) to **RaspiNAS** (`192.168.1.10`).

**Mount (gateway Pi):** NFS or CIFS mount to a dedicated backup path on the NAS, e.g.:

```
/mnt/nas-backup/pivpngateway/
```

**Schedule:** `systemd` timer or cron on the gateway — e.g. every 6 hours and after every registry change (hook from API in Phase 6). Keep last N copies (`registry.json`, timestamped snapshots).

**Restore:** Copy latest `registry.json` back to `/etc/pivpngateway/`, run `apply-policy-routing.sh`, `apply-firewall.sh`, `generate-dnsmasq.sh`.

NAS mount credentials and exact export path — configure at Phase 3/6 build time.

---

## 13. Verification checklist

Run these after cutover.

### From a normal LAN device (default — both off)

- [ ] Default gateway = `GATEWAY_IP`
- [ ] DNS = public (`1.1.1.1` / `8.8.8.8`) unless opted into Pi-hole
- [ ] Public IP = home ISP (`curl -s https://ifconfig.me`) unless opted into VPN
- [ ] Internet and ads behave like pre-gateway cutover

### From a device opted into VPN

- [ ] Public IP ≠ home ISP; [Obscura check connection](https://obscura.com/) connected

### From a device opted into Pi-hole

- [ ] DNS = `.11` / `.4`; ad blocking works

### From a device with both opted in

- [ ] Obscura + Pi-hole + no DNS leak

### From phone / laptop on inbound VPN (away from home)

Per §8.5 — **always Obscura + Pi-hole** while connected, regardless of WiFi toggles:

- [ ] Can reach LAN services (Home Assistant, NAS, Pi-hole admin)
- [ ] Public IP ≠ home ISP; [Obscura check connection](https://obscura.com/) connected while browsing
- [ ] DNS = `.11` / `.4`; ads blocked
- [ ] Public IP is Obscura, not home ISP
- [ ] DNS leak test — no ISP DNS servers

### From PiFirewall (always bypass)

- [ ] Backup WireGuard tunnel active; VPN mode toggle disabled in UI

### From the gateway Pi

- [ ] `sudo wg show` — recent handshake, rx/tx increasing
- [ ] `sudo iptables -L -n -v` — rules present, FORWARD policy DROP
- [ ] `sudo systemctl status dnsmasq` — active
- [ ] Kill switch: Obscura down → **routed** device loses internet; **bypass** devices still work

### Services that must keep working

- [ ] Pi-hole admin (`.4` and `.11`) — queries logged, blocking works
- [ ] Inbound VPN on gateway — phone/laptop clients connect; old `.4` server disabled
- [ ] PiFirewall (`.28`) — backup WireGuard tunnel active
- [ ] Home Assistant (`.38`), cameras, NAS — LAN access OK
- [ ] Prometheus (`.16`) — gateway target UP

### Gateway throughput A/B test (build baseline)

Run on a **wired, reserved** test host (e.g. Pi5Desktop `.42`). Use the same device and same speed test site for all runs (e.g. fast.com, speedtest.net, or `iperf3` to a known host). Run tests **one at a time**, a few minutes apart, same time of day if possible.

**Before Phase 4 (DHCP cutover):** Test host must use the Pi as gateway — temporary static IP with gateway = `GATEWAY_IP`, or R8000 reservation + manual gateway override on the test host only.

| Run | Path | How | Expected |
|-----|------|-----|----------|
| **A — Reference** | Device → R8000 (`.1`) → ISP | Test host gateway = **`192.168.1.1`** (bypass Pi) | Best-case ISP/WiFi-off/wired ceiling |
| **B — Gateway bypass** | Device → Pi (bypass policy) → R8000 → ISP | Test host gateway = **`GATEWAY_IP`**, registry `vpn_mode: bypass` | Should be **close to A**; large gap → Pi forwarding or double-hop issue |
| **C — Gateway routed** | Device → Pi → Obscura (`wg0`) → internet | Same gateway, registry `vpn_mode: routed` | Usually **≤ B**; WireGuard CPU or Obscura path |

**How to read results:**

| Pattern | Likely bottleneck |
|---------|-------------------|
| **A ≈ B ≈ C** (all similar) | Not the Pi — probably ISP or test server |
| **A ≈ B >> C** (C much slower) | Obscura tunnel or Pi WireGuard crypto — normal to some degree |
| **A >> B ≈ C** | Pi forwarding path or policy routing — investigate before cutover |
| **A >> B >> C** | Both Pi path and Obscura add cost — note baseline Mbps for later Grafana comparison |

**While running C (optional but useful):** On the gateway, watch CPU and drops:

```bash
htop                          # softirq / single-core pegged → Pi at crypto limit
watch -n1 'wg show wg0'       # rx/tx increasing during test
ethtool -S eth0 | grep -i drop   # rising drops → overload
vcgencmd get_throttled        # 0x0 = no throttling
```

After Phase 5, the same test should appear in Grafana as elevated `eth0` / `wg0` rates and CPU on the gateway scrape target — compare to the Mbps you recorded here.

**Checklist:**

- [ ] Run **A** — record download / upload Mbps
- [ ] Run **B** — record download / upload Mbps
- [ ] Run **C** — record download / upload Mbps
- [ ] Note gateway CPU/drops/throttle during **C**
- [ ] Repeat after Phase 5 (post-`node_exporter`) and file results as build baseline

---

## 14. Rollback plan

If something goes wrong during or after cutover:

### DHCP / LAN gateway rollback

1. **Re-enable DHCP on the R8000** — Advanced → Setup → LAN Setup → “Use Router as DHCP Server”.
2. Clients will gradually pick up gateway `192.168.1.1` again on lease renewal (or force renew).
3. Stop dnsmasq on the Pi: `sudo systemctl stop dnsmasq`.
4. Optionally power off the gateway Pi entirely — the network should behave as it did before cutover.

**Have a wired path to the router admin UI** before cutover. If WiFi DHCP breaks, you need another way in.

### Inbound VPN / port-forward rollback (Phase 2+)

The R8000 can forward **UDP 51820** to **only one** LAN host. Once Phase 2 points it at `GATEWAY_IP`, remote clients **cannot** reach PiHole-Main (`.4`) until you change the forward back.

**To restore pre-gateway remote access:**

1. On the gateway: `sudo systemctl stop wg-quick@home` (stop inbound server on `wg1`).
2. On PiHole-Main (`.4`): re-enable the previous WireGuard **server** config (keep a backup from Phase 0 export).
3. **R8000:** change port forward UDP `51820` → **`192.168.1.4`** (was `GATEWAY_IP`).
4. Phone/laptop: use **old** WireGuard client profiles (pointing at `.4` keys) until gateway is fixed.
5. Leave outbound Obscura/`wg0` on the gateway stopped if the Pi is causing LAN routing issues.

**Partial rollback:** If only Obscura outbound is broken but LAN routing works, you can leave DHCP on the Pi and disable `wg-quick@obscura` — bypass devices keep internet via `.1`; routed devices lose WAN (kill switch behavior).

Document the **current** port-forward target in §18 at build time so rollback is one router screen, not archaeology.

---

## 15. Risks and tradeoffs

| Risk | Mitigation |
|------|------------|
| Gateway Pi is a **single point of failure** (DHCP, VPN routing, remote access) | Document rollback (§14); keep PiHole-Main inbound config backup; know port-forward revert steps |
| DHCP cutover affects **every device at once** | Test dnsmasq first; cut over during a maintenance window |
| Kill switch misconfiguration → **leak** or **total outage** | Test tunnel-down scenario before cutover; narrow LAN-to-LAN rules |
| Bypass misconfiguration → **break backup tunnel or work VPN** | Policy routing + iptables; test `.28` and work laptops explicitly |
| Inbound VPN misconfiguration → **remote clients leak or can't reach LAN** | Test from outside WiFi before decommissioning `.4`; verify `wg1` → `wg0` path |
| IPv6 leak | Disable on Pi and R8000 |
| Unknown devices with static gateway `.1` | Audit devices with hardcoded gateway during pre-build inventory |
| Docker on gateway Pi | **Avoid** running Docker on the gateway — it interferes with iptables |
| Management API exposed beyond LAN | Bind API to LAN only; token auth; PiSensors proxies requests |

---

## 16. Open items

Track in this section until closed at build time.

### Before the Pi arrives

| Item | Status |
|------|--------|
| Final `GATEWAY_IP` (e.g., `.100`) | **TBD** |
| Obscura account and WireGuard config | **TBD** |
| Export inbound VPN config from PiHole-Main (`.4`) | **TBD** |

### During gateway build (Phases 1–5)

| Item | Status |
|------|--------|
| Gateway Pi MAC address | **TBD** — first boot |
| Inbound VPN client subnet (e.g. `10.66.66.0/24`) | **TBD** — Phase 2 |
| New WireGuard client configs (phone, laptop) | **PiVPN** on gateway — Phase 2–3 |
| R8000 port forward UDP 51820 → `GATEWAY_IP` | **TBD** — Phase 2 |
| Fresh router export (Attached Devices + Reservations) | **TBD** — Phase 4 |
| Final dnsmasq reservation file | **TBD** — Phase 4 |
| Pi-hole upstream DNS (Obscura-compatible) | **TBD** — Phase 4 |
| `/etc/pivpngateway/` registry + apply scripts | **TBD** — Phase 3 (§12.1) |
| NAS backup mount + schedule (`.10`) | **TBD** — Phase 3/6 (§12.2) |

### After gateway is stable (Phases 6–7)

| Item | Status |
|------|--------|
| Management API stack and auth | **TBD** — Phase 6 (API token + PiSensors basic password) |
| PiSensors UI routes and port | **TBD** — Phase 6 |
| Per-client metrics on gateway (`/metrics`) | **TBD** — Phase 6 |
| Grafana per-device traffic dashboards | **TBD** — Phase 7 |

---

## 17. Related files in this folder

| File | Purpose |
|------|---------|
| `PROJECT.md` (this file) | Project definition and build requirements — **maintained** |
| `UI-MOCKS.md` | Wireframe mocks for PiSensors management UI (v1) — **maintained** |

Other notes (e.g. `NetworkInventory.docx`, earlier setup guides) may exist locally but are **not** kept in sync with this repo — use live router export and §16 open items at build time instead.

---

## 18. Quick reference (fill in at build time)

```
Gateway hostname:  pivpngateway
Gateway IP:        GATEWAY_IP          (candidate: 192.168.1.100)
Gateway MAC:       TBD
Router:            192.168.1.1         (R8000 — wan_gateway_ip in Settings)
Pi-hole DNS:       192.168.1.11 / .4   (pihole_dns_* in Settings)
Public DNS:        1.1.1.1 / 8.8.8.8   (default_public_dns_* in Settings)
Monitoring:        192.168.1.16        (Prometheus / Grafana — ssh ubuntu@.16)
Outbound VPN:      Obscura / WireGuard (wg0)
Inbound VPN:       Gateway wg1 :51820  (replaces PiHole-Main .4)
LAN subnet:        192.168.1.0/24
Remote client net: TBD                 (e.g. 10.66.66.0/24)
Gateway API:       http://GATEWAY_IP:8080  (LAN only — TBD port)
Management UI:     http://192.168.1.26:8000/gateway  (PiSensors — basic password)
Registry backup:   /mnt/nas-backup/pivpngateway/     (RaspiNAS .10 — mount TBD)
Grafana:           http://192.168.1.16:3000  (historical traffic dashboards)
Prometheus:        http://192.168.1.16:9090  (metrics store)
Throughput A/B:    See §13 — record Phase 3 + Phase 5 baseline Mbps (A/B/C)
```

---

*When the Pi arrives, start with Phase 1 (§12) and refresh §16 open items from a live router export before touching DHCP.*
