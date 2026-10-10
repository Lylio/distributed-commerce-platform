import { useRef, useState } from 'react';
import { api } from './api';
import { customerId } from './cart';
import type { CartLine, Product, Stock } from './types';
import { money, ErrorNotice } from './ui';
import { ProductArt } from './ProductArt';
interface Props { cart: CartLine[]; products: Product[]; stock: Stock[]; stockError: string; checkout: boolean; change: (id: string, quantity: number) => void; clear: () => void }
export function CartPage({ cart, products, stock, stockError, checkout, change, clear }: Props) {
  const [agreed, setAgreed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submitting = useRef(false);
  const resolved = cart.map(line => ({ ...line, product: products.find(product => product.id === line.productId), stock: stock.find(item => item.productId === line.productId) }));
  const total = resolved.reduce((sum, line) => sum + Math.round((line.product?.unitPrice || 0) * 100) * line.quantity, 0) / 100;
  const unavailable = !!stockError || resolved.some(line => !line.product || !line.stock || line.stock.availableQuantity < line.quantity);
  async function placeOrder(event: React.FormEvent) {
    event.preventDefault(); if (submitting.current) return;
    if (!agreed) { setError('Please acknowledge the demo payment before placing your order.'); return; }
    if (unavailable) { setError('Please update your bag to match the available stock.'); return; }
    submitting.current = true; setBusy(true); setError('');
    try {
      // Only identifiers and quantities leave the browser. Prices are resolved by Spring Boot.
      const order = await api.createOrder(customerId(), cart);
      clear(); window.location.hash = `/orders/${order.id}`;
    } catch (failure) { setError((failure as Error).message); }
    finally { submitting.current = false; setBusy(false); }
  }
  if (!cart.length) return <main className="page empty-page"><span className="eyebrow">A little space for something good</span><h1>Your bag is empty.</h1><p>Find a new favourite for your everyday.</p><a className="button primary" href="#/shop">Explore the collection <span aria-hidden="true">↗</span></a></main>;
  return <main className="page"><a className="back-link" href={checkout ? '#/cart' : '#/shop'}>← {checkout ? 'Back to your bag' : 'Keep exploring'}</a><div className="page-heading"><div><span className="eyebrow">{checkout ? 'One last look' : 'Your considered essentials'}</span><h1>{checkout ? 'Make it yours.' : 'Your bag.'}</h1></div><span>{cart.reduce((n, line) => n + line.quantity, 0)} items</span></div>
    <div className="checkout-layout"><section aria-label="Bag items" className="cart-items">{resolved.map(line => <article className="cart-item" key={line.productId}>
      <div className={`mini-art tone-${line.product?.color || 'sand'}`}><ProductArt kind={line.product?.illustration || 'hub'}/></div>
      <div className="cart-item-info"><span className="eyebrow">{line.product?.category || 'Unavailable'}</span><h2>{line.product?.name || 'Product no longer available'}</h2><p>{line.product ? money(line.product.unitPrice, line.product.currency) : 'Remove this item to continue'}</p>
        {(!line.stock || line.stock.availableQuantity < line.quantity) && <p className="stock-warning">{line.stock ? `Only ${line.stock.availableQuantity} available` : 'Stock cannot be verified'}</p>}
        <div className="quantity"><button aria-label={`Decrease ${line.product?.name || 'item'} quantity`} disabled={busy} onClick={() => change(line.productId, line.quantity - 1)}>−</button><input type="number" min="1" max="100" aria-label={`${line.product?.name || 'Item'} quantity`} value={line.quantity} disabled={busy} onChange={event => change(line.productId, Number(event.target.value))}/><button aria-label={`Increase ${line.product?.name || 'item'} quantity`} disabled={busy || line.quantity >= Math.min(100, line.stock?.availableQuantity || 0)} onClick={() => change(line.productId, line.quantity + 1)}>+</button></div>
      </div><div className="cart-item-right"><strong>{line.product ? money(line.product.unitPrice * line.quantity, line.product.currency) : '—'}</strong><button className="text-button muted" disabled={busy} onClick={() => change(line.productId, 0)} aria-label={`Remove ${line.product?.name || 'item'}`}>Remove</button></div>
    </article>)}</section>
    <aside className="order-summary"><span className="eyebrow">The details</span><h2>Order summary</h2><div className="summary-row"><span>Items</span><span>{money(total)}</span></div><div className="summary-row"><span>Additional charges</span><span>None in this demo</span></div><div className="summary-row total"><span>Total</span><strong>{money(total)}</strong></div><p className="fine-print">Final prices are calculated by the store when your order is submitted. Stock is reserved after submission.</p>
      {stockError && <ErrorNotice message={stockError}/>}
      {unavailable && !stockError && <div className="notice error">Some items exceed available stock. Update your bag to continue.</div>}
      {checkout ? <form onSubmit={placeOrder}><div className="payment-method"><span className="demo-tag">DEMO PAYMENT</span><h3>Try the whole journey.</h3><p>No card details. No money is charged. The demo processor returns a success or failure and updates your order.</p></div><label className="checkbox-label"><input type="checkbox" checked={agreed} onChange={event => setAgreed(event.target.checked)} disabled={busy}/>I understand this is a demo purchase.</label>{error && <ErrorNotice message={error}/>}<button className="button primary full" type="submit" disabled={busy || unavailable}>{busy ? 'Placing your order…' : 'Place demo order'} <span aria-hidden="true">↗</span></button><p className="fine-print">This demo collects no contact or delivery details. Your order is saved with a customer reference local to this browser.</p></form> : <a className={`button primary full ${unavailable ? 'disabled' : ''}`} href={unavailable ? undefined : '#/checkout'} aria-disabled={unavailable}>Continue to checkout <span aria-hidden="true">↗</span></a>}
    </aside></div></main>;
}
