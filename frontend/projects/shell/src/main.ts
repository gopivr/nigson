import { initFederation } from '@angular-architects/native-federation';

async function bootstrap() {
  const maxRetries = 5;
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      await initFederation('federation.manifest.json', {
        hostRemoteEntry: { url: './remoteEntry.json' },
      });
      await import('./bootstrap');
      return;
    } catch (err) {
      if (attempt < maxRetries) {
        console.warn(`[Shell] Remote micro-frontends still initializing (attempt ${attempt}/${maxRetries}). Retrying in 2s...`);
        await new Promise(r => setTimeout(r, 2000));
      } else {
        console.warn('[Shell] Could not initialize all remote micro-frontends; booting with local fallbacks.');
        await import('./bootstrap');
        return;
      }
    }
  }
}

bootstrap();
