# Deploying CampusDesk to Vercel

CampusDesk is ready to deploy directly to **Vercel** using Vercel's **Container Runtime**.

---

## How It Works

Vercel natively supports containerized backend servers via `Dockerfile.vercel` at the root of the repository.
- When imported into Vercel, Vercel automatically detects `Dockerfile.vercel`.
- It builds the lightweight multi-stage image using Eclipse Temurin Java JRE.
- Vercel provisions a dynamic `$PORT` and routes incoming HTTPS web requests directly to your Java HTTP server.
- The application automatically seeds default accounts (`admin` / `admin123`) and initial sample courses and students.

---

## Option 1: Deploy via GitHub (Recommended)

1. **Commit and push** your latest changes to your GitHub repository:
   ```bash
   git add .
   git commit -m "Configure Vercel Container deployment"
   git push origin main
   ```

2. **Go to Vercel Dashboard:**
   - Log in at [vercel.com](https://vercel.com).
   - Click **Add New...** -> **Project**.
   - Select your GitHub account and find the **CampusDesk** repository (`lokeshjhuria/CampusDesk`).
   - Click **Import**.

3. **Deploy:**
   - Vercel will automatically detect `Dockerfile.vercel`.
   - Leave the default settings (no build override needed).
   - Click **Deploy**.
   - Vercel will build the container and provide your live production URL (e.g., `https://campusdesk-xxxx.vercel.app`).

---

## Option 2: Deploy via Vercel CLI

If you prefer deploying directly from your terminal:

1. Install the Vercel CLI globally (if not already installed):
   ```bash
   npm install -g vercel
   ```

2. Log in to Vercel:
   ```bash
   vercel login
   ```

3. Deploy from the project root:
   ```bash
   vercel
   ```
   Follow the prompts to link the project and deploy.

4. To deploy to production:
   ```bash
   vercel --prod
   ```

---

## Sign In & Default Credentials

Once deployed, open your live Vercel URL and sign in:
- **Username:** `admin`
- **Password:** `admin123`

You can also use **Create an account** on the sign-in page to register new accounts.

---

## Architecture & Notes

- **Dynamic Port:** The app reads the `$PORT` environment variable provided by Vercel.
- **Host Binding:** In cloud containers, the server binds to `0.0.0.0` to receive external web traffic.
- **Stateless/Ephemeral Storage:** Vercel Fluid compute containers scale down when idle. Any new instance automatically re-seeds default data from `FileHandler.java` if the data directory is empty.
