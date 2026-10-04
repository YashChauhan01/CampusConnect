import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { AdminZone } from '../api/types';
import { mockApi } from '../test/mockApi';
import { AdminZonesPage } from './AdminZonesPage';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ profile: { admin: true } }),
}));

const library: AdminZone = { id: 1, name: 'Library', enabled: true, x: 0, y: 0, checkedIn: 3 };
const lab: AdminZone = { id: 2, name: 'Computer Lab', enabled: false, x: null, y: null, checkedIn: 0 };

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminZonesPage />
    </MemoryRouter>,
  );
}

describe('AdminZonesPage', () => {
  it('lists zones with position, occupancy and status, and plots the placed ones', async () => {
    mockApi({ 'GET /admin/zones': { body: [library, lab] } });
    renderPage();

    expect(await screen.findByRole('cell', { name: 'Library' })).toBeInTheDocument();
    expect(screen.getByText('0, 0')).toBeInTheDocument();
    expect(screen.getByText('not placed')).toBeInTheDocument();
    expect(screen.getByText('Disabled')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Map of campus zones' })).toBeInTheDocument();
  });

  it('creates a zone with numeric coordinates', async () => {
    const { calls } = mockApi({
      'GET /admin/zones': { body: [library] },
      'POST /admin/zones': { status: 201, body: { ...library, id: 9, name: 'Gym' } },
    });
    renderPage();
    const user = userEvent.setup();

    await screen.findByRole('cell', { name: 'Library' });
    await user.type(screen.getByLabelText('Name'), 'Gym');
    await user.type(screen.getByLabelText('X (m)'), '120.5');
    await user.type(screen.getByLabelText('Y (m)'), '-40');
    await user.click(screen.getByRole('button', { name: 'Add zone' }));

    expect(calls.find((c) => c.key === 'POST /admin/zones')?.body).toEqual({ name: 'Gym', x: 120.5, y: -40 });
  });

  it('sends null coordinates for an unplaced zone', async () => {
    const { calls } = mockApi({
      'GET /admin/zones': { body: [library] },
      'POST /admin/zones': { status: 201, body: { ...library, id: 9, name: 'Quad', x: null, y: null } },
    });
    renderPage();
    const user = userEvent.setup();

    await screen.findByRole('cell', { name: 'Library' });
    await user.type(screen.getByLabelText('Name'), 'Quad');
    await user.click(screen.getByRole('button', { name: 'Add zone' }));

    expect(calls.find((c) => c.key === 'POST /admin/zones')?.body).toEqual({ name: 'Quad', x: null, y: null });
  });

  it('shows the server error when a name is taken', async () => {
    mockApi({
      'GET /admin/zones': { body: [library] },
      'POST /admin/zones': { status: 409, body: { error: 'A zone with this name already exists', code: 'CONFLICT' } },
    });
    renderPage();
    const user = userEvent.setup();

    await screen.findByRole('cell', { name: 'Library' });
    await user.type(screen.getByLabelText('Name'), 'library');
    await user.click(screen.getByRole('button', { name: 'Add zone' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('already exists');
  });

  it('disables an active zone while keeping its position', async () => {
    const { calls } = mockApi({
      'GET /admin/zones': { body: [library] },
      'PUT /admin/zones/1': { body: { ...library, enabled: false } },
    });
    renderPage();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Disable' }));

    expect(calls.find((c) => c.key === 'PUT /admin/zones/1')?.body).toEqual({ name: 'Library', x: 0, y: 0, enabled: false });
  });

  it('edits a zone inline', async () => {
    const { calls } = mockApi({
      'GET /admin/zones': { body: [library] },
      'PUT /admin/zones/1': { body: { ...library, name: 'Main Library', x: 5 } },
    });
    renderPage();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const name = screen.getByLabelText('Name of Library');
    await user.clear(name);
    await user.type(name, 'Main Library');
    const x = screen.getByLabelText('X of Library');
    await user.clear(x);
    await user.type(x, '5');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(calls.find((c) => c.key === 'PUT /admin/zones/1')?.body).toEqual({ name: 'Main Library', x: 5, y: 0, enabled: true });
  });
});
