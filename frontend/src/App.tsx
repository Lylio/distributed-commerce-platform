import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { changeQuantity, readCart, saveCart } from './cart';
import { CartPage } from './CartPage';
import { OrderReceipt, OrdersPage } from './OrdersPage';
import { ProductArt } from './ProductArt';
import type { Product, Stock } from './types';
import { BagIcon, ErrorNotice, money } from './ui';
function useRoute() {
  const [route, setRoute] = useState(() => window.location.hash.slice(1) || '/shop');
  useEffect(() => { const change = () => setRoute(window.location.hash.slice(1) || '/shop'); window.addEventListener('hashchange', change); return () => window.removeEventListener('hashchange', change); }, []);
  useEffect(() => { window.scrollTo?.({ top: 0, behavior: 'instant' }); }, [route]);
  return route;
}
function ProductDialog({ product, available, onClose, add }: { product: Product; available: number; onClose: () => void; add: (product: Product) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { dialog.current?.showModal(); }, []);
  return <dialog ref={dialog} className="product-dialog" onClose={onClose} onClick={event => { if (event.target === event.currentTarget) dialog.current?.close(); }} aria-labelledby="product-title">
    <button className="dialog-close" aria-label="Close product details" onClick={() => dialog.current?.close()}>×</button><div className={`dialog-art tone-${product.color}`}><ProductArt kind={product.illustration}/></div><div className="dialog-content"><span className="eyebrow">{product.category}</span><h2 id="product-title">{product.name}</h2><p className="lead">{product.subtitle}</p><strong className="detail-price">{money(product.unitPrice, product.currency)}</strong><p>{product.description}</p><ul className="features">{product.features.map(feature => <li key={feature}>{feature}</li>)}</ul><p className={`availability ${available ? '' : 'unavailable'}`}>{available > 0 ? `${available} available · Stock checked live` : available < 0 ? 'Stock temporarily unavailable' : 'Currently out of stock'}</p><button className="button primary full" disabled={available <= 0} onClick={() => add(product)}>Add to bag <span aria-hidden="true">+</span></button><p className="fine-print">Demo collection. No actual payment or delivery takes place.</p></div>
  </dialog>;
}
export default function App() {
  const route = useRoute();
  const [products, setProducts] = useState<Product[]>([]);
  const [stock, setStock] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState('');
  const [stockError, setStockError] = useState('');
  const [cart, setCart] = useState(readCart);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('All');
  const [inStock, setInStock] = useState(false);
  const [sort, setSort] = useState('featured');
  const [selected, setSelected] = useState<Product | null>(null);
  const [notification, setNotification] = useState('');
  const [retry, setRetry] = useState(0);
  const noticeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => { saveCart(cart); }, [cart]);
  useEffect(() => { const changed = (event: StorageEvent) => { if (event.key === 'form.cart.v1') setCart(readCart()); }; window.addEventListener('storage', changed); return () => window.removeEventListener('storage', changed); }, []);
  useEffect(() => {
    let stopped = false; let timer: ReturnType<typeof setTimeout>;
    async function refreshStock() {
      try { const value = await api.inventory(); if (!stopped) { setStock(value); setStockError(''); } }
      catch { if (!stopped) setStockError('Live stock is unavailable. Start Inventory Service or try again before checking out.'); }
      finally { if (!stopped) timer = setTimeout(refreshStock, 4000); }
    }
    setLoading(true);
    void Promise.allSettled([api.products().then(value => { if (!stopped) { setProducts(value); setCatalogError(''); } }).catch(failure => { if (!stopped) setCatalogError((failure as Error).message); }), refreshStock()]).then(() => { if (!stopped) setLoading(false); });
    return () => { stopped = true; clearTimeout(timer); };
  }, [retry, route]);
  useEffect(() => () => clearTimeout(noticeTimer.current), []);
  function add(product: Product) {
    const available = stock.find(item => item.productId === product.id)?.availableQuantity || 0;
    const quantity = cart.find(item => item.productId === product.id)?.quantity || 0;
    if (stockError || quantity >= Math.min(available, 100)) { notify('You already have the available quantity in your bag.'); return; }
    setCart(value => changeQuantity(value, product.id, quantity + 1)); notify(`${product.name} added to your bag.`);
  }
  function notify(message: string) { setNotification(message); clearTimeout(noticeTimer.current); noticeTimer.current = setTimeout(() => setNotification(''), 4500); }
  const count = cart.reduce((sum, line) => sum + line.quantity, 0);
  const categories = ['All', ...new Set(products.map(product => product.category))];
  const visible = products.filter(product => (category === 'All' || category === product.category)
    && `${product.name} ${product.category} ${product.description}`.toLowerCase().includes(search.trim().toLowerCase())
    && (!inStock || (!stockError && (stock.find(item => item.productId === product.id)?.availableQuantity || 0) > 0)))
    .sort((a, b) => sort === 'price-low' ? a.unitPrice - b.unitPrice : sort === 'price-high' ? b.unitPrice - a.unitPrice : 0);
  const shop = !['/cart', '/checkout', '/orders'].includes(route) && !route.startsWith('/orders/');
  return <><a href="#main-content" className="skip-link" onClick={event => { event.preventDefault(); document.getElementById('main-content')?.focus(); }}>Skip to content</a><div className="announcement">Considered essentials. Real orders. Demo payments.</div><header className="site-header"><a className="brand" href="#/shop" aria-label="FORM home">form<span>.</span></a><nav aria-label="Main navigation"><a href="#/shop" aria-current={shop ? 'page' : undefined}>Collection</a><a href="#/orders" aria-current={route.startsWith('/orders') ? 'page' : undefined}>Order dashboard</a></nav><a className="bag-link" href="#/cart" aria-label={`Shopping bag, ${count} items`} aria-current={route === '/cart' ? 'page' : undefined}><BagIcon/><span>Bag</span><span className="bag-count">{count}</span></a></header>
    <div id="main-content" tabIndex={-1}>
    {!shop && catalogError && <div className="page"><ErrorNotice message={catalogError} retry={() => setRetry(value => value + 1)}/></div>}
    {shop ? <main><section className="hero"><div className="hero-copy"><span className="eyebrow">Objects for a more considered everyday</span><h1>Good things.<br/><em>Better days.</em></h1><p>A little intention goes a long way. Discover the essentials that make your workspace feel more like you.</p><a className="button primary" href="#collection" onClick={event => { event.preventDefault(); document.getElementById('collection')?.scrollIntoView({ behavior: 'smooth' }); }}>Explore the collection <span aria-hidden="true">↗</span></a><div className="hero-footnote"><span className="line"/> Less noise. More room for what matters.</div></div><div className="hero-visual tone-sage"><div className="hero-label"><span>THE EVERYDAY EDIT</span><span>01 / 06</span></div><ProductArt kind="keyboard" className="hero-product"/><div className="hero-bottom"><span>Made for your<br/><strong>kind of work.</strong></span><span className="roundel">Thoughtfully<br/>chosen<br/>↗</span></div></div></section>
      <section className="collection page" id="collection"><div className="section-heading"><div><span className="eyebrow">Small changes. A better space.</span><h2>The collection<span>.</span></h2></div><p>Every piece has its place.<br/>Find yours.</p></div><div className="collection-controls"><label className="search"><svg width="19" height="19" viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="10" cy="10" r="6.5" stroke="currentColor" strokeWidth="1.5"/><path d="m15 15 5 5" stroke="currentColor" strokeWidth="1.5"/></svg><input type="search" placeholder="Find your everyday essential" aria-label="Search products" value={search} onChange={event => setSearch(event.target.value)}/></label><label className="sort">Sort by <select aria-label="Sort products" value={sort} onChange={event => setSort(event.target.value)}><option value="featured">Featured</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option></select></label></div><div className="filter-row"><div className="category-filters" role="group" aria-label="Product categories">{categories.map(value => <button key={value} aria-pressed={category === value} className={category === value ? 'active' : ''} onClick={() => setCategory(value)}>{value}</button>)}</div><label className="checkbox-label"><input type="checkbox" checked={inStock} onChange={event => setInStock(event.target.checked)}/>In stock only</label></div>
      {catalogError && <ErrorNotice message={catalogError} retry={() => setRetry(value => value + 1)}/>} {stockError && <ErrorNotice message={stockError} retry={() => setRetry(value => value + 1)}/>}
      {loading ? <div className="skeleton-grid" aria-label="Loading products" role="status">{Array.from({ length: 6 }, (_, i) => <div key={i} className="skeleton"/>)}</div> : <><div className="results-count">{visible.length} thoughtfully chosen {visible.length === 1 ? 'essential' : 'essentials'}</div><div className="product-grid">{visible.map((product, i) => { const available = stockError ? -1 : stock.find(item => item.productId === product.id)?.availableQuantity ?? -1; return <article className="product-card" key={product.id}><button className={`product-image tone-${product.color}`} aria-label={`View ${product.name} details`} onClick={() => setSelected(product)}><span className={`stock-chip ${available <= 0 ? 'stock-muted' : ''}`}>{available > 0 ? available < 10 ? 'Low stock' : 'In stock' : available < 0 ? 'Stock unavailable' : 'Out of stock'}</span><ProductArt kind={product.illustration}/><span className="image-corner">{String(i + 1).padStart(2, '0')} <span aria-hidden="true">↗</span></span></button><div className="product-meta"><span className="eyebrow">{product.category}</span><span>{money(product.unitPrice, product.currency)}</span></div><h3><button onClick={() => setSelected(product)}>{product.name}</button></h3><p>{product.subtitle}</p><button className="add-button" disabled={available <= 0 || (cart.find(item => item.productId === product.id)?.quantity || 0) >= Math.min(available, 100)} onClick={() => add(product)} aria-label={`Add ${product.name} to bag`}>{available === 0 ? 'Currently unavailable' : 'Add to bag'} <span aria-hidden="true">+</span></button></article>; })}</div>{!visible.length && !catalogError && <div className="empty-state"><h3>No essentials found.</h3><p>Try a different search or category.</p><button className="text-button" onClick={() => { setSearch(''); setCategory('All'); setInStock(false); }}>Clear filters</button></div>}</>}
      </section><section className="editorial"><span className="eyebrow">A little intention</span><h2>Make room for<br/><em>the things you love.</em></h2><p>Good design feels at home. Start with one small change.</p><a className="text-button" href="#/cart">See your selection ↗</a></section></main>
      : route === '/cart' || route === '/checkout' ? (loading && !products.length ? <main className="page"><p role="status">Loading your bag and checking stock…</p></main> : <CartPage key={route} cart={cart} products={products} stock={stock} stockError={stockError} checkout={route === '/checkout'} change={(id, quantity) => setCart(value => changeQuantity(value, id, quantity))} clear={() => setCart([])}/>)
      : route === '/orders' ? <OrdersPage products={products} stock={stock} stockError={stockError}/>
      : <OrderReceipt id={route.slice('/orders/'.length)}/>}
    </div><footer className="site-footer"><div><a className="brand" href="#/shop">form<span>.</span></a><p>A better everyday, thoughtfully chosen.</p></div><div><span className="eyebrow">A working commerce demo</span><p>Real inventory. Real order history.<br/>Simulated payments. No actual delivery.</p></div><span className="footer-note">Made with a little intention.</span></footer>
    {notification && <div className="toast" role="status">{notification}<a href="#/cart">View bag ↗</a></div>}{selected && <ProductDialog key={selected.id} product={selected} available={stockError ? -1 : stock.find(item => item.productId === selected.id)?.availableQuantity ?? -1} onClose={() => setSelected(null)} add={add}/>}</>;
}
