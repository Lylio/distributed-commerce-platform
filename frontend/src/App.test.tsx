import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { api } from './api';
import type { Product, Stock } from './types';
vi.mock('./api', () => ({ api: { products: vi.fn(), inventory: vi.fn(), orders: vi.fn(), order: vi.fn(), createOrder: vi.fn() } }));
const product: Product = { id: '22222222-2222-2222-2222-222222222222', name: 'Mechanical Keyboard', subtitle: 'The tactile essential', category: 'Workspace', description: 'A considered keyboard.', features: ['USB-C connection'], illustration: 'keyboard', color: 'sage', unitPrice: 79.99, currency: 'GBP' };
const headphones: Product = { ...product, id: '33333333-3333-3333-3333-333333333333', name: 'Studio Headphones', description: 'Balanced sound for listening.', category: 'Audio', illustration: 'headphones', unitPrice: 129 };
const inventory: Stock[] = [{ productId: product.id, availableQuantity: 10, reservedQuantity: 0 }, { productId: headphones.id, availableQuantity: 0, reservedQuantity: 0 }];
beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); window.location.hash = '/shop'; vi.spyOn(window, 'scrollTo').mockImplementation(() => {}); vi.mocked(api.products).mockResolvedValue([product, headphones]); vi.mocked(api.inventory).mockResolvedValue(inventory); vi.mocked(api.orders).mockResolvedValue([]); });
describe('shopping interface', () => {
  it('searches the catalogue and disables unavailable products', async () => {
    const user = userEvent.setup(); render(<App/>);
    expect(await screen.findByRole('button', { name: 'Add Studio Headphones to bag' })).toBeDisabled();
    await user.type(screen.getByRole('searchbox'), 'keyboard');
    expect(screen.queryByRole('button', { name: 'View Studio Headphones details' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View Mechanical Keyboard details' })).toBeVisible();
  });
  it('shows real product details and feature strings', async () => {
    const user = userEvent.setup(); render(<App/>);
    await user.click(await screen.findByRole('button', { name: 'View Mechanical Keyboard details' }));
    expect(within(screen.getByRole('dialog')).getByText('USB-C connection')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Close product details' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('persists additions and removal', async () => {
    const user = userEvent.setup(); render(<App/>);
    await user.click(await screen.findByRole('button', { name: 'Add Mechanical Keyboard to bag' }));
    await waitFor(() => expect(JSON.parse(localStorage.getItem('form.cart.v1')!)).toEqual([{ productId: product.id, quantity: 1 }]));
    await user.click(screen.getByRole('link', { name: 'Shopping bag, 1 items' }));
    await user.click(await screen.findByRole('button', { name: 'Remove Mechanical Keyboard' }));
    expect(await screen.findByRole('heading', { name: 'Your bag is empty.' })).toBeVisible();
  });
  it('keeps the cart and reports a failed checkout instead of showing fake success', async () => {
    localStorage.setItem('form.cart.v1', JSON.stringify([{ productId: product.id, quantity: 2 }])); window.location.hash = '/checkout';
    vi.mocked(api.createOrder).mockRejectedValue(new Error('Order service unavailable'));
    const user = userEvent.setup(); render(<App/>);
    await screen.findByRole('button', { name: 'Place demo order' });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Place demo order' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Place demo order' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Please acknowledge the demo payment');
    expect(api.createOrder).not.toHaveBeenCalled();
    await user.click(screen.getByRole('checkbox', { name: 'I understand this is a demo purchase.' }));
    await user.click(screen.getByRole('button', { name: 'Place demo order' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Order service unavailable');
    expect(api.createOrder).toHaveBeenCalledWith(expect.stringMatching(/^[\da-f-]{36}$/), [{ productId: product.id, quantity: 2 }]);
    expect(JSON.parse(localStorage.getItem('form.cart.v1')!)).toHaveLength(1);
  });
  it('fails closed when live stock is unavailable', async () => {
    vi.mocked(api.inventory).mockRejectedValue(new Error('offline')); render(<App/>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Live stock is unavailable');
    expect(await screen.findByRole('button', { name: 'Add Mechanical Keyboard to bag' })).toBeDisabled();
  });
  it('shows a catalogue retry state', async () => {
    vi.mocked(api.products).mockRejectedValueOnce(new Error('Unable to load catalogue'));
    const user = userEvent.setup(); render(<App/>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load catalogue');
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('button', { name: 'View Mechanical Keyboard details' })).toBeVisible();
  });
});
