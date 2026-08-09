# VPS MySQL Runbook

How to check whether the production database is healthy, how to look at the
data, and how to diagnose/fix it when something's wrong. Written up after a
full audit on 2026-08-06 that found one real bug (see "Case study" at the
bottom) — use this doc to redo that same audit yourself next time, instead of
starting from scratch.

## 1. Connect to the VPS

```bash
ssh careown-vps-deploy
```

This uses the SSH alias already set up in `~/.ssh/config` (host
`careown-vps-deploy`, points at `187.127.156.179` as the `deploy` user, which
has full passwordless `sudo`). If that alias is ever missing on a machine,
here's what it looks like:

```
Host careown-vps-deploy
    HostName 187.127.156.179
    User deploy
    IdentityFile ~/.ssh/careown_vps_ed25519
    IdentitiesOnly yes
```

Everything below assumes you're either running commands locally as
`ssh careown-vps-deploy "<command>"`, or you've SSH'd in interactively and are
running them directly. Commands that touch MySQL or system files need `sudo`
in front — the underlying files are owned by the `careown` service account,
not `deploy`.

## 2. The architecture, in one paragraph

There are **two databases**, not one. `hms_master` is a small registry (6
tables: `client`, `client_database`, `client_module`, `client_feature_flag`,
`super_admin_user`) that just tracks which hospital "clients" exist and where
each one's real database lives. `hms_demo` is the actual hospital — patients,
appointments, billing, everything — currently the one and only tenant,
registered in `hms_master.client_database` with `status='READY'`. The app
reads `hms_master` first to figure out which tenant DB to route to, then
talks to that tenant DB for everything else. If you ever see a second
hospital client added, it'll get its own `hms_<name>` database and a new row
in `client_database` — `hms_master` never holds clinical data itself.

## 3. Quick health check (run this first, always)

```bash
# Is MySQL up?
sudo systemctl status mysql --no-pager

# Is the app up, and did it start cleanly?
sudo systemctl status hms-api --no-pager
sudo journalctl -u hms-api -n 60 --no-pager

# Does the public API actually respond? (run from your own machine, not the VPS)
curl https://api.careownitsolutions.com/actuator/health
# Healthy response: {"groups":["liveness","readiness"],"status":"UP"}
```

If all three come back clean, the system is healthy end-to-end — MySQL
running, the app connected to both databases, and the public internet path
(nginx → app → MySQL) working. Most "is something broken" questions stop
right here.

## 4. Looking at the data

```bash
# What databases exist?
sudo mysql -e "SHOW DATABASES;"

# What tables are in each?
sudo mysql -e "SELECT table_name FROM information_schema.tables WHERE table_schema='hms_demo';"
sudo mysql -e "SELECT table_name FROM information_schema.tables WHERE table_schema='hms_master';"

# Look at real rows
sudo mysql hms_demo -e "SELECT * FROM patient LIMIT 10;"
sudo mysql hms_demo -e "SELECT * FROM general_user LIMIT 10;"
sudo mysql hms_master -e "SELECT * FROM client;"
sudo mysql hms_master -e "SELECT * FROM client_database;"

# Row counts, if you just want a sanity check that data exists at all
sudo mysql hms_demo -e "SELECT COUNT(*) FROM patient;"
sudo mysql hms_demo -e "SELECT COUNT(*) FROM general_user;"
```

To open an interactive session instead of one-off `-e` queries:

```bash
sudo mysql hms_demo
# then just type SQL, e.g.: SELECT * FROM patient\G
```

## 5. Checking the schema is up to date (Flyway)

The app runs database migrations automatically every time it starts — you
never need to run these by hand. To check what version each database is
actually at:

```bash
sudo mysql hms_demo -e "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
sudo mysql hms_master -e "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

Compare the highest `version` number against the migration files in the repo
(`hms-api/src/main/resources/db/migration/` for tenant, `db/migration-master/`
for master — the highest `V<n>__*.sql` filename should match). If the number
in the database is lower than the highest file in the repo, either the app
hasn't restarted since that migration was added, or something failed —
check `journalctl -u hms-api` for a Flyway error around startup.

## 6. Checklist for "why isn't login/data/something working"

Work through these in order — each one only matters if the previous one
passed:

1. **Is MySQL running?** `sudo systemctl status mysql`
2. **Did the app start cleanly?** `sudo journalctl -u hms-api -n 100 --no-pager` — look for `Started HmsApiApplication` near the bottom with no errors above it. Two Flyway blocks should appear: one for `hms_master`, one for whichever tenant DB is `READY`.
3. **Is there a `DEFAULT` client row?** `sudo mysql hms_master -e "SELECT * FROM client WHERE code='DEFAULT';"` — if this is empty, single-tenant login can't resolve at all (the app throws `"No 'DEFAULT' client seeded in the master database."`).
4. **Is that client's database marked READY?** `sudo mysql hms_master -e "SELECT * FROM client_database;"` — `status` must be `READY`, not `PROVISIONING` or `FAILED`, or the tenant DB never gets migrated/routed to.
5. **Does the public health endpoint respond?** `curl https://api.careownitsolutions.com/actuator/health` — if this fails but everything above passed, the problem is likely nginx or the network path, not the database. Check `sudo cat /etc/nginx/sites-enabled/api.careownitsolutions.com.conf` and `sudo systemctl status nginx`.
6. **Does an actual login work?** Test with `curl`, not just the health endpoint — health checks that MySQL is reachable, not that credentials/JWT issuance actually work end to end:
   ```bash
   curl -X POST https://api.careownitsolutions.com/api/super-admin/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"superadmin","password":"<the real password>"}'
   ```
   `HTTP 200` with a token back means the whole chain — nginx, app, JWT signing, master DB, bcrypt check — is genuinely working, not just "the process is running."

## 7. Backups — where they are, and how to confirm they're real

```bash
# Where backups live
sudo ls -la /home/CareOwn_HMS/backups/mysql/daily/
sudo ls -la /home/CareOwn_HMS/backups/mysql/monthly/

# When did the job last run, and did it back up the right things?
sudo tail -10 /home/CareOwn_HMS/logs/backup.log

# The script itself, and its schedule
sudo cat /home/CareOwn_HMS/scripts/backup.sh
crontab -l -u careown   # should show: 15 2 * * * /home/CareOwn_HMS/scripts/backup.sh
```

**Don't just trust that a file exists — check it actually contains the right
data.** A backup script can "succeed" (exit 0, log "backup ok") while
silently dumping the wrong thing, which is exactly what happened in the case
study below. To actually verify a dump is real:

```bash
# File size sanity check — hms_demo should be tens of KB (real clinical data),
# hms_master should be a couple KB (it's just a small registry)
sudo ls -la /home/CareOwn_HMS/backups/mysql/daily/ | tail -6

# Peek inside without fully extracting — confirm it has the tables you expect
sudo bash -c "zcat /home/CareOwn_HMS/backups/mysql/daily/hms_demo-<timestamp>.sql.gz | grep -m5 'CREATE TABLE'"

# Confirm it has real rows, not just an empty schema
sudo bash -c "zcat /home/CareOwn_HMS/backups/mysql/daily/hms_demo-<timestamp>.sql.gz | grep -m1 'INSERT INTO \`patient\`'"
```

## 8. Seeding a Super Admin account

If `hms_master.super_admin_user` is empty, nobody can log into the Super
Admin portal. To add one:

```bash
# 1. On your own machine, generate a bcrypt hash (matches Spring Security's
#    BCryptPasswordEncoder — cost factor 10). Needs Node + bcryptjs:
npm install bcryptjs --no-save
node -e "console.log(require('bcryptjs').hashSync(process.argv[1], 10))" 'YourPasswordHere'

# 2. Insert it into the master DB (replace the hash with the one you just generated)
sudo mysql hms_master -e "INSERT INTO super_admin_user (username, password_hash, created_at) VALUES ('superadmin', '<bcrypt-hash>', NOW());"

# 3. Prove it actually works end-to-end (see the curl example in section 6)
```

Never type the plaintext password directly into a `mysql -e` command or log
file — only the bcrypt hash should ever touch the database or shell history.

## Case study: the backup bug found on 2026-08-06

**Symptom**: none, visibly — the system looked and worked fine.

**What was actually wrong**: `backup.sh` figured out which database to dump
by parsing it out of `DB_URL` in the app's env file
(`/home/CareOwn_HMS/hms-api/shared/hms-api.env`). That used to be fine when
there was only one database. But once the app moved to the master/tenant
split (`DB_URL` now points at `hms_master`, the small registry; the real
clinical data moved to `hms_demo`, referenced separately as a tenant), the
backup script kept deriving its target from `DB_URL` — so it silently started
backing up the small 6-table registry every night, **under a filename that
still said `hms_demo`**, while the real clinical data got zero backup
coverage.

**Why it wasn't caught sooner**: the script never failed. `mysqldump` ran
successfully every night, `backup.log` said "backup ok," retention rotated
the files on schedule — every signal you'd normally check said "this is
fine." The only way to catch it was to open a backup file and check *what's
actually inside it*, which is why step 7 above emphasizes not trusting the
filename or exit code alone.

**The fix**: changed the script to dump both `hms_master` and `hms_demo` by
name explicitly, rather than deriving one name dynamically from `DB_URL` —
so it can't silently drop coverage again if `DB_URL` ever gets repointed a
third time.

**Lesson for next time**: whenever the app's environment config changes
(especially `DB_URL`/`TENANT_DB_URL`), grep for anything else on the VPS that
also reads those same variables — `backup.sh` wasn't part of the app
deployment, so it was easy to forget it existed and depended on the same
value.
