import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { MatchView, PresenceView, Profile } from './api/types';
import { connectBlocker } from './components/Connect';
import { Modal } from './components/Modal';
import { checkInRequest } from './lib-presence';
import { CampusPage } from './pages/CampusPage';
import { mockApi } from './test/mockApi';

vi.mock('./realtime/RealtimeContext', () => ({ useRealtime: () => ({ connected: true, version: () => 0 }) }));

const profile: Profile = {
  id: 'me', fullName: 'Asha Rao', email: 'asha@college.edu', bio: null, admin: false, skills: [],
  subjects: [
    { id: 7, name: 'DBMS', proficiency: 'ADVANCED', verifiedLevel: null },
    { id: 8, name: 'DSA', proficiency: 'INTERMEDIATE', verifiedLevel: null },
  ],
};
vi.mock('./auth/AuthContext', () => ({ useAuth: () => ({ profile, setProfile: vi.fn() }) }));

const presenceOf = (id: string, name: string, zoneId: number, zone: string, over: Partial<PresenceView> = {}): PresenceView => ({
  studentId: id, name, zoneId, zone, status: 'AVAILABLE', requirements: null, seeking: [], subjects: [],
  expiresAt: new Date(Date.now() + 600_000).toISOString(), ...over,
});
const breakdown = { knowledge: 1, reciprocity: 1, breadth: 0.33, proximity: 1 };
const match = (over: Partial<MatchView> = {}): MatchView => ({
  id: 'm1', status: 'PROPOSED', score: 88, breakdown, partner: { id: 'r', name: 'Ravi Patel', zone: 'Library', bio: null, email: null },
  sharedSubjects: ['DBMS'], youAccepted: false, partnerAccepted: false, createdAt: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 300_000).toISOString(), direct: false, requestedByYou: false, ...over,
});

describe('checkInRequest', () => {
  it('is a plain available check-in when the student is not on campus', () => {
    expect(checkInRequest(3, undefined, profile)).toEqual({ zoneId: 3, status: 'AVAILABLE' });
  });

  it('keeps status, note and subjects when renewing or moving', () => {
    const current = presenceOf('me', 'Asha', 1, 'Library', { status: 'BUSY', requirements: 'Quiz tomorrow', seeking: ['DSA', 'Unknown'] });
    expect(checkInRequest(2, current, profile)).toEqual({ zoneId: 2, status: 'BUSY', requirements: 'Quiz tomorrow', seekingSubjectIds: [8] });
  });
});

describe('connectBlocker', () => {
  it('treats an unanswered automatic proposal as a suggestion that a request replaces', () => {
    expect(connectBlocker(match(), true)).toBeNull();
  });

  it('still blocks once someone has answered the proposal, or for a live match', () => {
    expect(connectBlocker(match({ partnerAccepted: true }), true)).toBe('Answer your pending match first');
    expect(connectBlocker(match({ direct: true, requestedByYou: true, youAccepted: true }), true)).toBe('Request pending with Ravi Patel');
    expect(connectBlocker(match({ status: 'ACCEPTED' }), true)).toBe('You are matched with Ravi Patel');
    expect(connectBlocker(undefined, false)).toBe('Check in on campus to send requests');
  });
});

describe('Modal', () => {
  it('keeps the cursor in a field while the parent re-renders with a new onClose', async () => {
    function Host() {
      const [ticks, setTicks] = useState(0);
      return (
        <>
          <button type="button" onClick={() => setTicks(ticks + 1)}>tick {ticks}</button>
          <Modal title="Form" onClose={() => undefined}>
            <input aria-label="Subject" />
          </Modal>
        </>
      );
    }
    const user = userEvent.setup();
    render(<Host />);
    const input = screen.getByLabelText('Subject');
    await user.click(input);
    await user.type(input, 'Op');
    // A re-render caused from outside the dialog (the campus page does this every second).
    act(() => screen.getByRole('button', { name: /tick/, hidden: true }).click());
    await waitFor(() => expect(screen.getByRole('button', { name: /tick 1/, hidden: true })).toBeInTheDocument());
    expect(input).toHaveFocus();
    await user.type(input, 'en');
    expect(input).toHaveValue('Open');
    expect(input).toHaveFocus();
  });

  it('closes on Escape using the latest onClose', async () => {
    const first = vi.fn();
    const second = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(<Modal title="Form" onClose={first}>x</Modal>);
    rerender(<Modal title="Form" onClose={second}>x</Modal>);
    await user.keyboard('{Escape}');
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });
});

describe('Campus map zone panel', () => {
  it('opens a panel for the clicked zone, moves the check-in there and keeps the note', async () => {
    const mine = presenceOf('me', 'Asha Rao', 1, 'Library', { requirements: 'Revising for the quiz', seeking: ['DBMS'] });
    const { calls } = mockApi({
      'GET /presence/zones': { body: [{ id: 1, name: 'Library' }, { id: 2, name: 'Cafeteria' }] },
      'GET /presence/me': { body: mine },
      'GET /presence/available': { body: [presenceOf('a', 'Ravi Patel', 1, 'Library'), presenceOf('b', 'Meera Shah', 2, 'Cafeteria')] },
      'GET /matching/matches/current': { status: 204 },
      'GET /matching/suggestions': { body: [] },
      'POST /presence/check-in': { body: presenceOf('me', 'Asha Rao', 2, 'Cafeteria', { requirements: 'Revising for the quiz', seeking: ['DBMS'] }) },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <CampusPage />
      </MemoryRouter>,
    );
    await user.click(await screen.findByRole('button', { name: /Cafeteria: 1 peers/ }));
    const panel = await screen.findByRole('heading', { name: 'Cafeteria', level: 2 });
    expect(panel).toBeInTheDocument();
    expect(screen.getAllByText('Meera Shah').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /Move here/ }));
    await waitFor(() => expect(calls.some((c) => c.key === 'POST /presence/check-in')).toBe(true));
    expect(calls.find((c) => c.key === 'POST /presence/check-in')?.body).toEqual({
      zoneId: 2, status: 'AVAILABLE', requirements: 'Revising for the quiz', seekingSubjectIds: [7],
    });

    await user.click(screen.getByRole('button', { name: /Show all zones/ }));
    expect(screen.queryByRole('heading', { name: 'Cafeteria', level: 2 })).not.toBeInTheDocument();
    expect(within(document.body).queryByRole('button', { name: /Move here/ })).not.toBeInTheDocument();
  });
});
