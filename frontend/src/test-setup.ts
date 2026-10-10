import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
afterEach(cleanup);
Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { value: function () { this.open = true; } });
Object.defineProperty(HTMLDialogElement.prototype, 'close', { value: function () { this.open = false; this.dispatchEvent(new Event('close')); } });
// Node 25 also exposes an experimental localStorage; use the real jsdom browser storage.
Object.defineProperty(globalThis, 'localStorage', { value: window.localStorage, configurable: true });
