# Smart Reply AI

Android app + small Node.js server for preparing suggested replies to up to 100 Facebook comments at a time.

## Current app behavior
- Paste up to 100 comments, one comment per line.
- Send the batch to your own HTTPS server.
- The server calls the OpenAI Responses API and returns a structured result for each comment.
- The app displays a suggested reply and whether a Like is recommended.
- Copy the results and review them before using them.

## Important Facebook limitation
This project does **not** log into Facebook, scrape comments, click Like, or publish replies. The current official Meta APIs do not provide equivalent automated engagement access to a personal profile. Do not add browser/Accessibility click automation to bypass this limitation; it can put the account at risk. Page-level integrations require a Meta app, permissions, review where applicable, and a Page access token.

## Deploy the AI server
1. Deploy the `server/` folder to a Node.js hosting provider that supports HTTPS and Node 20+.
2. Set these environment variables in the host dashboard:
   - `OPENAI_API_KEY`: your OpenAI API key (never put it in the Android app or GitHub).
   - `APP_ACCESS_TOKEN`: a long random secret used by the app to authenticate to your server.
   - `OPENAI_MODEL`: optional; defaults to `gpt-6-astra`.
3. Start command: `npm start`.
4. Confirm `https://YOUR-HOST/health` returns `{"ok":true,...}`.
5. In the Android app, enter `https://YOUR-HOST/api/replies` and the same `APP_ACCESS_TOKEN`.

See `.env.example` for variable names. Do not commit real secrets.

## Android build
GitHub Actions builds a debug APK on every push to `main`. The artifact is named `SmartReplyAI-debug-apk`.

## API contract
Request:
```json
{
  "post_url": "https://www.facebook.com/...",
  "language_style": "auto",
  "comments": [
    {"id": "1", "text": "محتوى التعليق"}
  ]
}
```

Response:
```json
{
  "items": [
    {
      "id": "1",
      "reply": "الرد المقترح",
      "like_recommended": true,
      "reason": "تعليق إيجابي"
    }
  ]
}
```
