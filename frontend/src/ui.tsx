import type { OrderStatus } from './types';
export const money = (value: number, currency = 'GBP') => new Intl.NumberFormat('en-GB', { style: 'currency', currency }).format(value);
export const date = (value: string) => new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
export const statusLabel: Record<OrderStatus, string> = { PENDING: 'Order received', INVENTORY_RESERVED: 'Stock reserved', PAYMENT_PENDING: 'Payment processing', CONFIRMED: 'Confirmed', CANCELLED: 'Cancelled' };
export function Status({ status }: { status: OrderStatus }) { return <span className={`status status-${status.toLowerCase()}`}><span/>{statusLabel[status]}</span>; }
export function ErrorNotice({ message, retry }: { message: string; retry?: () => void }) { return <div className="notice error" role="alert"><span>{message}</span>{retry && <button className="text-button" onClick={retry}>Try again</button>}</div>; }
export function BagIcon() { return <svg width="20" height="22" viewBox="0 0 24 26" fill="none" aria-hidden="true"><path d="M4 8h16l2 15H2L4 8Z" stroke="currentColor" strokeWidth="1.5"/><path d="M8 9V6a4 4 0 0 1 8 0v3" stroke="currentColor" strokeWidth="1.5"/></svg>; }
