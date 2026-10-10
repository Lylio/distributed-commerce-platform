import { useEffect, useState } from 'react';
import { api } from './api';
import type { OrderDetail, Product, Stock } from './types';
import { date, ErrorNotice, money, Status, statusLabel } from './ui';
export function OrderReceipt({ id }: { id: string }) {
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let cancelled = false; let timeout: ReturnType<typeof setTimeout>; let attempts = 0;
    setDetail(null); setError('');
    async function refresh() {
      try {
        const value = await api.order(id); if (cancelled) return;
        setDetail(value); setError('');
        if (!['CONFIRMED', 'CANCELLED'].includes(value.order.status) && ++attempts < 80) timeout = setTimeout(refresh, 1500);
      } catch (failure) { if (!cancelled) setError((failure as Error).message); }
    }
    void refresh(); return () => { cancelled = true; clearTimeout(timeout); };
  }, [id, retry]);
  return <main className="page receipt"><a className="back-link" href="#/orders">← Order dashboard</a><span className="eyebrow">Your order, in motion</span><h1>{detail?.order.status === 'CONFIRMED' ? 'Good things are yours.' : detail?.order.status === 'CANCELLED' ? 'This order was cancelled.' : 'Order received.'}</h1><p className="lead">{detail?.order.status === 'CONFIRMED' ? 'Your stock reservation and simulated payment are complete.' : detail?.order.status === 'CANCELLED' ? 'See the outcome below. No real payment was taken.' : 'We’re checking stock and processing your demo payment. This page updates automatically.'}</p>{error && <ErrorNotice message={error} retry={() => setRetry(value => value + 1)}/>}
    {!detail && !error && <p role="status">Loading your order…</p>}{detail && <OrderInformation detail={detail}/>}<a className="button primary" href="#/shop">Back to the collection <span aria-hidden="true">↗</span></a>{detail && !['CONFIRMED', 'CANCELLED'].includes(detail.order.status) && <p className="fine-print">If processing takes longer, your order remains saved. Follow it from the dashboard or <button className="text-button" onClick={() => setRetry(value => value + 1)}>refresh its status</button>.</p>}</main>;
}
export function OrderInformation({ detail }: { detail: OrderDetail }) {
  return <section className="order-information"><div className="order-info-top"><div><span className="eyebrow">Order reference</span><p className="order-reference">{detail.order.id}</p></div><Status status={detail.order.status}/></div><div className="order-detail-grid"><div><h3>Order history</h3><ol className="timeline">{detail.transitions.map((item, i) => <li key={`${item.status}-${i}`}><span>{statusLabel[item.status]}</span><time>{date(item.occurredAt)}</time></li>)}</ol></div><div><h3>Payment & items</h3><p className="demo-outcome">{detail.paymentStatus === 'SUCCEEDED' ? 'Demo payment succeeded' : detail.paymentStatus === 'FAILED' ? 'Demo payment failed' : detail.order.status === 'CANCELLED' ? 'Payment not requested' : 'Awaiting demo payment'}</p>{detail.cancellationReason && <p className="stock-warning">{detail.cancellationReason === 'PAYMENT_FAILED' ? 'Payment was declined by the simulator. Stock release is processed separately.' : detail.cancellationReason.startsWith('INSUFFICIENT_STOCK') ? 'There was not enough stock to fulfil this order. No payment was requested.' : 'An ordered product could not be reserved. No payment was requested.'}</p>}<ul className="receipt-items">{detail.order.items.map((item, i) => <li key={`${item.productId}-${i}`}><span>{item.quantity} × {item.productName}</span><strong>{money(item.total, detail.currency)}</strong></li>)}</ul><div className="summary-row total"><span>Total</span><strong>{money(detail.order.total, detail.currency)}</strong></div></div></div></section>;
}
export function OrdersPage({ products, stock, stockError }: { products: Product[]; stock: Stock[]; stockError: string }) {
  const [orders, setOrders] = useState<OrderDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let stopped = false; let timer: ReturnType<typeof setTimeout>;
    async function refresh() {
      try { const value = await api.orders(); if (!stopped) { setOrders(value); setError(''); } }
      catch (failure) { if (!stopped) setError((failure as Error).message); }
      finally { if (!stopped) { setLoading(false); timer = setTimeout(refresh, 3000); } }
    }
    void refresh(); return () => { stopped = true; clearTimeout(timer); };
  }, [retry]);
  const visible = orders.filter(detail => filter === 'ALL' || detail.order.status === filter);
  const active = orders.find(detail => detail.order.id === selected);
  return <main className="page dashboard"><div className="page-heading"><div><span className="eyebrow">The demo workspace</span><h1>Keep things in view.</h1><p>Live orders, payment outcomes and stock across this store.</p></div><span className="live-label"><i/> Updates automatically</span></div><div className="notice subtle">Shared demo dashboard · All store orders are visible · Payments are simulated</div>
    <div className="metrics"><article><span>Orders in view</span><strong>{orders.length}</strong></article><article><span>Confirmed</span><strong>{orders.filter(detail => detail.order.status === 'CONFIRMED').length}</strong></article><article><span>Cancelled</span><strong>{orders.filter(detail => detail.order.status === 'CANCELLED').length}</strong></article><article><span>Available units</span><strong>{stockError ? '—' : stock.reduce((sum, item) => sum + item.availableQuantity, 0)}</strong></article></div>
    <section className="dashboard-section"><div className="section-title"><h2>Orders</h2><label>Status <select value={filter} onChange={event => setFilter(event.target.value)}><option value="ALL">All statuses</option>{Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>{error && <ErrorNotice message={error} retry={() => setRetry(value => value + 1)}/>}{loading && <p role="status">Loading orders…</p>}{!loading && !visible.length && <div className="empty-state"><h3>{orders.length ? 'No orders match this status.' : 'A clear desk. A fresh start.'}</h3><p>{orders.length ? 'Choose another status to see your orders.' : 'Place an order from the collection to see its progress here.'}</p></div>}{!!visible.length && <div className="table-scroll"><table><thead><tr><th>Reference / created</th><th>Items</th><th>Total</th><th>Status</th><th>Demo payment</th><th><span className="sr-only">Details</span></th></tr></thead><tbody>{visible.map(detail => <tr key={detail.order.id}><td><strong>#{detail.order.id.slice(0, 8)}</strong><small>{date(detail.order.createdAt)}</small></td><td>{detail.order.items.reduce((sum, item) => sum + item.quantity, 0)} items</td><td>{money(detail.order.total, detail.currency)}</td><td><Status status={detail.order.status}/></td><td>{detail.paymentStatus === 'NOT_REQUESTED' ? (detail.order.status === 'CANCELLED' ? 'Not requested' : 'Awaiting result') : detail.paymentStatus === 'SUCCEEDED' ? 'Succeeded' : 'Failed'}</td><td><button className="text-button" onClick={() => setSelected(selected === detail.order.id ? '' : detail.order.id)} aria-label={`View order ${detail.order.id.slice(0, 8)}`}>{selected === detail.order.id ? 'Close' : 'View'} ↗</button></td></tr>)}</tbody></table></div>}{orders.length === 200 && <p className="fine-print">Showing the latest 200 orders.</p>}</section>
    {active && <OrderInformation detail={active}/>}<section className="dashboard-section"><div className="section-title"><h2>Inventory</h2><span className="muted">Available & reserved</span></div>{stockError && <ErrorNotice message={stockError}/>}<div className="table-scroll"><table><thead><tr><th>Product</th><th>Available</th><th>Reserved</th><th>Availability</th></tr></thead><tbody>{products.map(product => { const inventory = stock.find(item => item.productId === product.id); return <tr key={product.id}><td><strong>{product.name}</strong><small>{product.category}</small></td><td>{stockError ? '—' : inventory?.availableQuantity ?? '—'}</td><td>{stockError ? '—' : inventory?.reservedQuantity ?? '—'}</td><td>{stockError || !inventory ? 'Unavailable' : inventory.availableQuantity ? 'In stock' : 'Out of stock'}</td></tr>; })}</tbody></table></div><p className="fine-print">Confirmed orders retain reserved stock in this demo. Payment-failure releases arrive asynchronously.</p></section>
  </main>;
}
