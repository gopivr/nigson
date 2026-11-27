import { fileURLToPath } from 'node:url';

export default {
  resolve: {
    alias: { 'shared-ui': fileURLToPath(new URL('./projects/shared-ui/src/public-api.ts', import.meta.url)) }
  },
  test: {
    environment: 'jsdom',
    include: [
      'projects/marketing/src/app/pages/careers.component.spec.ts',
      'projects/marketing/src/app/pages/distributor.component.spec.ts',
      'projects/shared-ui/src/lib/marketing.service.spec.ts',
      'projects/shared-ui/src/lib/google-recaptcha.service.spec.ts'
    ]
  }
};
