import base from './vitest.distributor.config.mjs';
export default { ...base, test: { ...base.test, include: ['projects/auth/src/app/signup/signup.component.spec.ts'] } };
