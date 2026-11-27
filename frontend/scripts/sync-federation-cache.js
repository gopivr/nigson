const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const cacheBase = path.join(root, 'node_modules', '.cache', 'native-federation');
const distBase = path.join(root, 'dist');

const mappings = {
  account: 'account',
  admin_cms: 'admin-cms',
  admin_core: 'admin-core',
  admin_shop: 'admin-shop',
  auth: 'auth',
  marketing: 'marketing',
  shell: 'shell',
  shop: 'shop'
};

if (fs.existsSync(cacheBase)) {
  for (const [cacheName, distName] of Object.entries(mappings)) {
    const src = path.join(cacheBase, cacheName);
    const dst = path.join(distBase, distName, 'browser');
    if (fs.existsSync(src)) {
      fs.mkdirSync(dst, { recursive: true });
      for (const file of fs.readdirSync(src)) {
        const sFile = path.join(src, file);
        const dFile = path.join(dst, file);
        if (fs.statSync(sFile).isFile()) {
          fs.copyFileSync(sFile, dFile);
        }
      }
    }
  }
}

