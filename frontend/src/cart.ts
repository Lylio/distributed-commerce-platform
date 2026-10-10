import type { CartLine } from './types';
const CART_KEY = 'form.cart.v1';
export function normaliseCart(value: unknown): CartLine[] {
  if (!Array.isArray(value)) return [];
  const quantities = new Map<string, number>();
  for (const line of value.slice(0, 100)) {
    if (!line || typeof line.productId !== 'string' || !/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(line.productId) || !Number.isInteger(line.quantity) || line.quantity < 1) continue;
    quantities.set(line.productId, Math.min(100, (quantities.get(line.productId) || 0) + line.quantity));
  }
  return [...quantities].map(([productId, quantity]) => ({ productId, quantity }));
}
export function readCart(): CartLine[] { try { return normaliseCart(JSON.parse(localStorage.getItem(CART_KEY) || '[]')); } catch { return []; } }
export function saveCart(cart: CartLine[]) { try { localStorage.setItem(CART_KEY, JSON.stringify(cart)); } catch { /* Browsers with storage disabled still support an in-memory cart. */ } }
export function changeQuantity(cart: CartLine[], productId: string, quantity: number): CartLine[] {
  if (!Number.isInteger(quantity)) return cart;
  if (quantity < 1) return cart.filter(line => line.productId !== productId);
  const capped = Math.min(quantity, 100);
  return cart.some(line => line.productId === productId) ? cart.map(line => line.productId === productId ? { ...line, quantity: capped } : line) : [...cart, { productId, quantity: capped }];
}
export function customerId(): string {
  try {
    const saved = localStorage.getItem('form.customer.v1');
    if (saved && /^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(saved)) return saved;
    const id = crypto.randomUUID(); localStorage.setItem('form.customer.v1', id); return id;
  } catch { return crypto.randomUUID(); }
}
