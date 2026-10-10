import { beforeEach, describe, expect, it } from 'vitest';
import { changeQuantity, normaliseCart, readCart, saveCart } from './cart';
const id = '22222222-2222-2222-2222-222222222222';
describe('persisted cart', () => {
  beforeEach(() => localStorage.clear());
  it('persists only identifiers and quantities', () => { saveCart([{ productId: id, quantity: 2 }]); expect(readCart()).toEqual([{ productId: id, quantity: 2 }]); });
  it('recovers from corrupt or malicious storage', () => { localStorage.setItem('form.cart.v1', '{broken'); expect(readCart()).toEqual([]); expect(normaliseCart([{ productId: id, quantity: -2 }, { productId: 'bad', quantity: 2 }, { productId: id, quantity: 1.2 }])).toEqual([]); });
  it('consolidates duplicate lines and caps quantities', () => { expect(normaliseCart([{ productId: id, quantity: 70 }, { productId: id, quantity: 70 }])).toEqual([{ productId: id, quantity: 100 }]); });
  it('adds, updates and removes a product', () => { let cart = changeQuantity([], id, 1); cart = changeQuantity(cart, id, 3); expect(cart).toEqual([{ productId: id, quantity: 3 }]); expect(changeQuantity(cart, id, 0)).toEqual([]); });
  it('rejects fractional quantity edits', () => { const cart = [{ productId: id, quantity: 2 }]; expect(changeQuantity(cart, id, 1.5)).toEqual(cart); });
});
