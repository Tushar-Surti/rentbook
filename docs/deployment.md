# Deploying Rentbook

The backend runs on Render and its Postgres database on Neon. The frontend runs on Vercel, which forwards `/api/*` to Render so the refresh cookie stays first-party. The browser opens the live-update WebSocket to Render directly, because Vercel cannot proxy WebSockets. Every piece fits in a free plan.

```
browser ──https──> Vercel (static React app)
   │                  └── /api/*  ──rewrite──> Render: rentbook-api (Spring Boot) ──> Neon Postgres
   └────── wss ──────────────────────────────> Render: rentbook-api /ws
Razorpay webhooks ────────────────────────────> Render: rentbook-api /api/v1/webhooks/razorpay
```

## What you need

- A GitHub repository with this code.
- Accounts on Render, Vercel and Neon.
- An email sender. The prod profile uses Brevo's SMTP relay (free for 300 emails a day): in Brevo, open **SMTP & API**, create an SMTP key, and note the login (`…@smtp-brevo.com`). The sender address must be one Brevo has verified, such as the email you signed up with. Any other relay works by setting `SMTP_HOST` and `SMTP_PORT` too.
- For online rent: a Razorpay account in test mode with Route enabled.
- For maintenance photos: a private Cloudflare R2 bucket (free egress) or an AWS S3 bucket, with an access key.
- Optional: Twilio for SMS reminders and urgent-request texts.

## 1. Database on Neon

1. In Neon, create a project in **AWS Asia Pacific (Singapore)**, the region closest to India and to Render's Singapore services. Neon's free plan keeps the database (0.5 GB) without expiring; it pauses when idle and wakes on the next connection.
2. Copy the connection string, then turn it into a JDBC URL for the **direct** host: drop `-pooler` from the host name, and keep `sslmode=require`. For example, `postgresql://user:pass@ep-x-pooler.….neon.tech/db?sslmode=require` becomes `jdbc:postgresql://ep-x.….neon.tech/db?sslmode=require`, with the user and password set separately. Flyway takes a session lock while it migrates, which Neon's pooler doesn't hold.

## 2. Backend on Render

1. In Render, choose **New > Blueprint** and pick the repository. Render reads `render.yaml` and proposes `rentbook-api`: a Docker web service built from `backend/Dockerfile`, in Singapore, on the free plan. A free instance sleeps after 15 minutes without traffic. The first request after that takes about a minute; Razorpay retries webhooks, so none are lost, but the nightly and reminder jobs only run while it's awake. Switch the plan to `starter` to keep it always on.
2. Render asks for the values marked `sync: false`. For the first deploy:
   - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: the Neon JDBC URL, user and password from section 1.
   - `RENTBOOK_APP_BASE_URL`: your Vercel URL, for example `https://rentbook.vercel.app`. Invite links point here. You can fill it in after section 3 and redeploy.
   - `RENTBOOK_CORS_ORIGINS`: the same Vercel URL. Comma-separate several (a custom domain, preview URLs).
   - `SMTP_USERNAME`, `SMTP_PASSWORD`: the Brevo SMTP login and SMTP key.
   - `MAIL_FROM`: the verified sender, for example `Rentbook <you@gmail.com>`.
   - `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`: from the Razorpay test dashboard (section 5). Leave them empty to keep online payments off; tenants then see that they should pay the way they do now.
3. Render generates `RENTBOOK_JWT_SECRET` itself. To set it by hand, use at least 32 random characters, for example the output of `openssl rand -base64 48`.
4. Deploy. On boot, Flyway creates the schema in Neon. The service is healthy when `https://<your-service>.onrender.com/actuator/health` answers `{"status":"UP"}`.

## 3. Frontend on Vercel

1. Edit `frontend/vercel.json` so the `/api/:path*` rewrite points at your Render service, if it is not named `rentbook-api`.
2. In Vercel, choose **Add New > Project**, import the repository and set:
   - Root directory: `frontend`
   - Framework preset: Vite (build command `npm run build`, output `dist`)
   - Environment variable `VITE_WS_URL` = `wss://<your-service>.onrender.com/ws`
3. Deploy, then copy the production URL into Render's `RENTBOOK_APP_BASE_URL` and `RENTBOOK_CORS_ORIGINS` and redeploy the backend.

## 4. Check it end to end

1. Open the Vercel URL and create a landlord account. A 6-digit code arrives from `MAIL_FROM`; enter it to finish. If it doesn't come, check spam, then the Brevo transactional log.
2. Add a property, then a room with two beds.
3. Invite a tenant to Bed A using an email address you can read. The email arrives from `MAIL_FROM`; the invite page also shows the link with Copy and WhatsApp buttons.
4. Open the link in a private window and accept. The tenant lands on their rent slip.
5. In the landlord's window, "Asha accepted your invite and has moved in." appears without a reload, and the register shows the tenant on Bed A.
6. Open the tenant from the register and add an electricity charge. The tenant's slip picks it up without a reload; both of them are reading the same rent book.
7. As the landlord, open **Payouts** and fill in the form. Test mode accepts dummy bank details, such as the IFSC `HDFC0001234`. The page changes to "Online rent is on" once Razorpay activates the linked account. If Razorpay refuses because Route onboarding through the API isn't enabled on your test account, ask Razorpay support to enable it.
8. As the tenant, pay from the slip with a test method, for example the UPI ID `success@razorpay`. The slip says it is waiting for the bank until Razorpay's webhook arrives, usually within seconds. Then the violet PAID stamp lands on the slip and on the landlord's register together.
9. Download the receipt from either side. In the Razorpay dashboard, check that the payment has a transfer to the landlord's linked account for 98% of the amount.
10. As the tenant, open **Requests**, report a problem and attach a photo. The landlord's page shows it under "Needs you" without a reload. Reply as the landlord and mark it being fixed; the tenant's thread follows along.
11. On the tenant's lease page, file the lease agreement under **Documents**. The tenant's **Documents** tab shows it straight away. File an ID document as the tenant and download the agreement from either side.
12. Open the browser console on any page. `vercel.json` sends the Content-Security-Policy as `Content-Security-Policy-Report-Only`, so violations are reported there without blocking anything. Once a test payment, a photo upload and a document download show no violations, rename the header to `Content-Security-Policy` and redeploy. If your API isn't on `onrender.com`, or storage isn't R2, adjust `connect-src` and `img-src` to match first.

## 5. More environment: payments, photos and SMS

| Feature | Variables | Also |
|---|---|---|
| Payments (available now) | `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | In the Razorpay test dashboard:<br>• confirm Route is enabled;<br>• set payment capture to automatic;<br>• add a webhook to `https://<your-service>.onrender.com/api/v1/webhooks/razorpay` for `order.paid`, `payment.captured`, `payment.failed` and the transfer events, using the same secret as `RAZORPAY_WEBHOOK_SECRET` (test mode asks for the OTP `754081`).<br><br>The webhook goes straight to Render, not through Vercel. To test webhooks against a local backend, tunnel with cloudflared or zrok; Razorpay refuses ngrok URLs. |
| Maintenance photos and the document vault (available now) | `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION` (`auto` for R2) | For R2:<br>• `S3_ENDPOINT` is `https://<account-id>.r2.cloudflarestorage.com`, with an R2 API token for the keys.<br>• Keep the bucket private.<br>• Add a CORS rule allowing the Vercel origin to `PUT` and `GET`, with the `Content-Type` header.<br><br>Browsers upload straight to the bucket with presigned URLs, so without that CORS rule, photo uploads fail in the browser while the API looks healthy. Until the keys are set, uploads answer 503 and requests work without photos. |
| SMS reminders (available now) | `RENTBOOK_SMS_PROVIDER=twilio`, plus `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` and `TWILIO_FROM` | Until you switch the provider, reminder texts are written to the log and email still goes out. Trial accounts only text verified numbers, and Indian DLT rules can filter SMS, so email stays the dependable channel for demos. |

## Railway instead of Render

Create a project with the Postgres plugin and a service from `backend/Dockerfile`. Set the same variables as above. Map Railway's database variables into `SPRING_DATASOURCE_URL=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}`, `SPRING_DATASOURCE_USERNAME=${PGUSER}` and `SPRING_DATASOURCE_PASSWORD=${PGPASSWORD}`. Railway provides `PORT`, which the backend reads.

## Custom domain (optional)

With `app.yourdomain.in` on Vercel and `api.yourdomain.in` on Render, both are one site. You can then call the API directly instead of through the rewrite. Update `RENTBOOK_CORS_ORIGINS`, `RENTBOOK_APP_BASE_URL` and `VITE_WS_URL` to match.

## When something is off

- **Startup fails with "rentbook.jwt.secret … must be at least 32 bytes":** set `RENTBOOK_JWT_SECRET`.
- **Every API call returns 403 from the browser:** the page's origin is missing from `RENTBOOK_CORS_ORIGINS`.
- **You are signed out on every reload:** the refresh cookie is not reaching the API. Requests must go through the `/api` rewrite on the Vercel domain, over HTTPS.
- **The live update line never appears:** check `VITE_WS_URL`, and that the Vercel origin is in `RENTBOOK_CORS_ORIGINS`; the WebSocket endpoint uses the same list.
- **Invite emails do not arrive:** the backend logs a warning with the recipient. The landlord can still copy or WhatsApp the link from the invite page.
- **Signing up answers 503 (`email_not_sent`):** the signup code couldn't be emailed. Check `SMTP_USERNAME` and `SMTP_PASSWORD`, and that Brevo has verified the `MAIL_FROM` address.
- **Startup fails with "fixed-email-code must not be set in production":** remove `RENTBOOK_AUTH_FIXEDEMAILCODE`. That setting is only for local development.
- **The tenant's Pay button is greyed out:** either the Razorpay keys are missing, or the landlord hasn't finished Payouts. `GET /api/v1/payouts/account` shows which: `paymentsConfigured` and `active`.
- **A payment stays on "waiting for the bank":** the webhook isn't landing. In the Razorpay dashboard, open the webhook's delivery log:
  - a 400 there means the secret differs from `RAZORPAY_WEBHOOK_SECRET`;
  - no attempts at all means the URL or the subscribed events are wrong.

  Nothing is marked paid until a delivery succeeds. Razorpay keeps retrying for 24 hours, and the tenant can't pay the same charges twice in that time.
- **Asking for a signup code or accepting an invite answers 429 (`too_many_signups`):** more than 20 signup codes or accounts were asked for from one address within the hour. Raise the limit with `RENTBOOK_AUTH_SIGNUPSPERHOUR` if many people really do sign up from one office network.
- **Photo uploads fail in the browser but the API looks healthy:** the bucket's CORS rules don't allow the Vercel origin to `PUT` with a `Content-Type` header.
