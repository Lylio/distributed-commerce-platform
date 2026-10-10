import type { CartLine, Order, OrderDetail, Product, Stock } from './types';
export class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try { response = await fetch(`/api${path}`, { ...options, headers: { 'Content-Type': 'application/json', ...options?.headers }, signal: AbortSignal.timeout(12000) }); }
  catch { throw new ApiError('We couldn’t reach the store. Check the backend services and try again.', 0); }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; message?: string };
    throw new ApiError(problem.detail || problem.message || `The request failed (${response.status}). Please try again.`, response.status);
  }
  return response.json() as Promise<T>;
}
export const api = {
  products: () => request<Product[]>('/products'),
  inventory: () => request<Stock[]>('/inventory'),
  orders: () => request<OrderDetail[]>('/orders?limit=200'),
  order: (id: string) => request<OrderDetail>(`/orders/${encodeURIComponent(id)}/details`),
  createOrder: (customerId: string, items: CartLine[]) => request<Order>('/orders', { method: 'POST', body: JSON.stringify({ customerId, items }) }),
};
