const express = require('express');
const {Issuer, generators} = require('openid-client');

const app = express();

const CLIENT_NAME = process.env.CLIENT_NAME || 'Web Client';
const CLIENT_ID = process.env.CLIENT_ID || 'client-a';
const CLIENT_SECRET = process.env.CLIENT_SECRET || 'client-secret';
const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://keycloak-midpoint.localhost';
const KEYCLOAK_INTERNAL_URL = process.env.KEYCLOAK_INTERNAL_URL || KEYCLOAK_URL;
const REALM = process.env.REALM || 'MidpointRealm';
const REDIRECT_URI = process.env.REDIRECT_URI || `http://${process.env.HOST || 'localhost'}/callback`;

const port = 3000;

let client;
let codeVerifier = generators.codeVerifier();
let codeChallenge = generators.codeChallenge(codeVerifier);

async function initClient() {
    const issuer = await Issuer.discover(`${KEYCLOAK_INTERNAL_URL}/realms/${REALM}`);
    client = new issuer.Client({
        client_id: CLIENT_ID,
        client_secret: CLIENT_SECRET,
        redirect_uris: [REDIRECT_URI],
        response_types: ['code'],
    });
    console.log(`[${CLIENT_NAME}] OIDC client initialized for realm ${REALM}`);
}

app.get('/', async (req, res) => {
    if (!client) {
        return res.status(500).send('OIDC client not initialized yet. Please retry in a moment.');
    }

    codeVerifier = generators.codeVerifier();
    codeChallenge = generators.codeChallenge(codeVerifier);

    const authUrl = client.authorizationUrl({
        scope: 'openid profile email',
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state: CLIENT_ID,
    });
    res.redirect(authUrl);
});

app.get('/callback', async (req, res) => {
    try {
        const tokenSet = await client.callback(REDIRECT_URI, req.query, {
            code_verifier: codeVerifier,
            state: CLIENT_ID,
        });

        const claims = tokenSet.claims();
        const permissions = claims.permissions || [];
        const username = claims.preferred_username || 'unknown';

        const permissionsHtml = permissions.length > 0
            ? permissions.map(p => `<li>${p}</li>`).join('')
            : '<li class="none">No permissions assigned</li>';

        res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${CLIENT_NAME}</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a2e;
            color: #eee;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
        }
        .container {
            background: #16213e;
            border-radius: 16px;
            padding: 48px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.3);
            max-width: 600px;
            width: 90%;
        }
        h1 {
            color: #e94560;
            font-size: 2em;
            margin-bottom: 8px;
        }
        .username {
            color: #0f3460;
            background: #e94560;
            padding: 4px 12px;
            border-radius: 8px;
            display: inline-block;
            font-weight: bold;
            margin-bottom: 24px;
        }
        h2 {
            color: #aaa;
            font-size: 1.1em;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 12px;
        }
        ul {
            list-style: none;
            padding: 0;
        }
        li {
            background: #0f3460;
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 8px;
            font-family: 'Courier New', monospace;
            font-size: 0.95em;
            border-left: 3px solid #e94560;
        }
        li.none {
            color: #666;
            font-style: italic;
            border-left-color: #333;
        }
        .token-info {
            margin-top: 24px;
            padding-top: 16px;
            border-top: 1px solid #333;
            color: #666;
            font-size: 0.8em;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>${CLIENT_NAME}</h1>
        <span class="username">${username}</span>
        <h2>Permissions</h2>
        <ul>
            ${permissionsHtml}
        </ul>
        <div class="token-info">
            Client ID: ${CLIENT_ID} | Realm: ${REALM}
        </div>
    </div>
</body>
</html>
        `);
    } catch (err) {
        console.error(`[${CLIENT_NAME}] Callback error:`, err.message);
        res.status(500).send(`Authentication error: ${err.message}`);
    }
});

app.get('/logout', (req, res) => {
    const logoutUrl = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout?client_id=${CLIENT_ID}&post_logout_redirect_uri=${REDIRECT_URI}`;
    res.redirect(logoutUrl);
});

app.listen(port, async () => {
    console.log(`[${CLIENT_NAME}] running on port ${port}`);
    try {
        await initClient();
    } catch (err) {
        console.error(`[${CLIENT_NAME}] Failed to initialize OIDC client:`, err.message);
        console.error(`[${CLIENT_NAME}] Will retry on first request`);
    }
});
