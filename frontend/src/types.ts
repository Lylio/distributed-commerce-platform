export interface Product { id: string; name: string; subtitle: string; category: string; description: string; features: string[]; illustration: string; color: string; unitPrice: number; currency: string }
export interface Stock { productId: string; availableQuantity: number; reservedQuantity: number }
export interface CartLine { productId: string; quantity: number }
export type OrderStatus = 'PENDING' | 'INVENTORY_RESERVED' | 'PAYMENT_PENDING' | 'CONFIRMED' | 'CANCELLED';
export interface Order { id: string; customerId: string; status: OrderStatus; total: number; createdAt: string; updatedAt: string; items: { productId: string; productName: string; quantity: number; unitPrice: number; total: number }[] }
export interface OrderDetail { order: Order; paymentStatus: 'NOT_REQUESTED' | 'SUCCEEDED' | 'FAILED'; cancellationReason: string | null; currency: string; transitions: { status: OrderStatus; occurredAt: string }[] }
