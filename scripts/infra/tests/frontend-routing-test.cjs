const fs = require('fs');
const vm = require('vm');
const assert = require('assert/strict');
const path = require('path');
const context = {};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../../infrastructure/terraform/aws/modules/frontend/routes.js'), 'utf8'), context);
for (const uri of ['/', '/products/sku', '/admin/users', '/auth/callback']) {
  assert.equal(context.handler({ request: { uri } }).uri, '/index.html');
}
for (const uri of ['/remotes/shop/remoteEntry.json', '/remotes/missing/', '/assets/missing.png', '/missing.js', '/federation.manifest.json']) {
  assert.equal(context.handler({ request: { uri } }).uri, uri);
}
for (const uri of ['/internal/v1/test', '/actuator/health']) {
  assert.equal(context.handler({ request: { uri } }).statusCode, 403);
}
console.log('PASS CloudFront navigation, strict remote/assets and private paths');
