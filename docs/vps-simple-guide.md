# Simple Guide: Connect to Your Hostinger VPS and Check the Database

This guide uses simple words. It explains how to connect to your server
(VPS) on Hostinger, and how to check your database (MySQL) yourself.

## First, some simple words explained

- **VPS** = your own private computer, running on the internet. Hostinger
  rents it to you. Your app and your database both live inside this
  computer.
- **Hostinger** = the company you rent the VPS from.
- **hPanel** = the website where you manage your Hostinger VPS (like a
  control panel, or dashboard).
- **SSH** = a way to open a text window that controls your VPS from far
  away. No mouse, only typing. Like a remote control, but with words.
- **MySQL** = the database software inside your VPS. It stores your
  hospital's information: patients, users, appointments, everything.
- **Database** = think of it like a big filing cabinet. Inside are
  **tables** (like drawers), and inside each table are **rows** (like single
  papers/files — one row = one patient, one user, etc.)

## Part 1: How to connect to your VPS through Hostinger

### Step 1 — Log in to Hostinger

1. Open your browser and go to **hpanel.hostinger.com**
2. Log in with your Hostinger email and password.

### Step 2 — Find your VPS

1. In the menu, click **VPS**.
2. Click on your VPS in the list (it may show a name like `srv1869966`, or a
   name you chose).
3. You will see your VPS's **IP address** — a set of numbers like
   `187.127.156.179`. This is your VPS's address on the internet, like a
   home address, but for a computer.

### Step 3 — Two ways to connect

**Option A — Browser Terminal (easiest, nothing to install)**

1. On your VPS page in hPanel, look for a tab or button called
   **SSH Access** or **Browser Terminal**.
2. Click it. A black text window opens right inside your browser.
3. This window is already connected to your VPS. You can start typing
   commands right away.

This is the easiest way if you just want to look around quickly.

**Option B — SSH from your own computer (what I use)**

This needs two things: your VPS's IP address, and either a password or a
saved "key" (a secure digital password file).

1. Open **PowerShell** on your Windows computer.
2. Type:
   ```
   ssh root@187.127.156.179
   ```
   (replace the numbers with your real VPS IP address from Step 2)
3. Type your password when asked, and press Enter.

For this project, there's already a saved shortcut set up on this machine,
so you (or I) don't need to type the full address every time:

```
ssh careown-vps-deploy
```

This one shortcut already knows the address, the username, and the secure
key needed to connect — so it just works right away.

## Part 2: Once you're connected — checking your database

Type these commands one at a time. Most database commands need the word
`sudo` in front — this means "do this as the admin," which is required to
touch the database files.

### Check 1 — Is the database program running?

```
sudo systemctl status mysql
```

Look for the word **active (running)**, usually shown in green. This means
MySQL (the database) is working.

### Check 2 — Is your app running?

```
sudo systemctl status hms-api
```

Same idea — look for **active (running)**.

### Check 3 — Does the website actually answer?

Open this link in your browser (or ask me to check it for you):

```
https://api.careownitsolutions.com/actuator/health
```

If it shows `{"status":"UP"}`, everything is working — the database, the
app, and the internet connection between them.

## Part 3: How to look at your actual data

```
# See the list of all databases
sudo mysql -e "SHOW DATABASES;"

# Look inside the hospital's database - see 10 patients
sudo mysql hms_demo -e "SELECT * FROM patient LIMIT 10;"

# Look inside the hospital's database - see the staff/user accounts
sudo mysql hms_demo -e "SELECT * FROM general_user LIMIT 10;"

# Just count how many patients exist (a quick "is there data at all?" check)
sudo mysql hms_demo -e "SELECT COUNT(*) FROM patient;"
```

If you want to just look around freely instead of typing one command at a
time, you can open an interactive session:

```
sudo mysql hms_demo
```

Now you're "inside" the database. Type SQL directly, for example:

```
SELECT * FROM patient;
```

When you're done, type `exit;` to leave.

## Part 4: Simple checklist if something seems wrong

Go through these one by one, in order. Stop at the first one that fails —
that tells you where the problem is.

1. **Can you log in to hPanel and see your VPS?**
   If no → this is a Hostinger account problem, contact Hostinger support.

2. **Can you connect with SSH (Part 1 above)?**
   If no → the VPS itself may be off, or your key/password is wrong.

3. **Is MySQL running?** (`sudo systemctl status mysql`)
   If no → MySQL crashed or was stopped. Try:
   ```
   sudo systemctl start mysql
   ```

4. **Is the app (hms-api) running?** (`sudo systemctl status hms-api`)
   If no → try restarting it:
   ```
   sudo systemctl restart hms-api
   sudo journalctl -u hms-api -n 50 --no-pager
   ```
   (this second command shows the last 50 log lines — look for red/error
   text near the bottom to see what went wrong)

5. **Does the health check link work?** (Part 2, Check 3 above)
   If no, but steps 3 and 4 were fine → the problem is likely the website
   connection (nginx) in front of the app, not the database itself.

6. **Does the data look normal?** (Part 3 above)
   If tables are empty when they shouldn't be, or counts look wrong → this
   is a data problem, not a connection problem. This is where you'd want
   help figuring out *why* the data is missing, rather than just confirming
   that it is.

## A word of caution

The database holds real patient information. A few simple safety rules:

- Commands that start with `SELECT` only **look** at data — always safe.
- Commands with `UPDATE`, `DELETE`, `INSERT`, or `DROP` **change** data —
  be careful, and ideally have someone double-check before running these on
  the real production database.
- Never share your VPS password, SSH key, or database password in chat,
  email, or anywhere outside of Hostinger/your password manager.
