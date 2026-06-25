const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

let certPath = '';
let keyPath = '';

// Read and parse the .env file if it exists
const envPath = path.join(__dirname, '.env');
if (fs.existsSync(envPath)) {
    const envFile = fs.readFileSync(envPath, 'utf-8');
    envFile.split('\n').forEach(line => {
        const [key, value] = line.split('=');
        if (key && value) {
            if (key.trim() === 'SSL_CERT_PATH') certPath = value.trim();
            if (key.trim() === 'SSL_KEY_PATH') keyPath = value.trim();
        }
    });
}


try {
    execSync(`npx ng serve --ssl-cert="${certPath}" --ssl-key="${keyPath}"`, { stdio: 'inherit' });
} catch (err) {
    // Gracefully handle server termination on Ctrl+C
}