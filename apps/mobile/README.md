# Finance Manager React Native

One Expo application targets web, iOS, and Android from the same TypeScript codebase.

## Run locally

```bash
npm install
npm run web
npm run ios
npm run android
```

Set `EXPO_PUBLIC_API_URL` when the backend is not available through the platform defaults:

- Web and iOS simulator: `http://localhost:8080/api/v1`
- Android emulator: `http://10.0.2.2:8080/api/v1`

The **Explore preview** action uses labelled sample data without calling the backend. Authenticated sessions use the documented `/api/v1` contracts and store tokens in SecureStore on native platforms and browser storage on web.

Registration with an email requires verification. Configure backend `CLIENT_PUBLIC_URL` to the deployed web-client origin; verification links use `?verificationToken=...`, which the shared app confirms on web and supported deep-link launches.

## Validate

```bash
npm run typecheck
npm test
npm run build:web
```
