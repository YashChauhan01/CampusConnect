import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { MatchView, NotificationView, PresenceView, Profile, Suggestion } from '../api/types';
import { CampusPage } from '../pages/CampusPage';
import { DashboardPage } from '../pages/DashboardPage';
import { NotificationsPage } from '../pages/NotificationsPage';
import { mockApi } from '../test/mockApi';
import { ChatPanel } from './ChatPanel';
import { ConnectButton, connectBlocker } from './Connect';
import { NotificationBell } from './NotificationBell';

const note = (id: string, title: string, read: boolean): NotificationView => ({
  id, type: 'MATCH_REQUESTED', title, body: `${title} body`, link: '/matches', createdAt: new Date().toISOString(), read,
});
const realtime = {
  inbox: { items: [note('n1', 'Study request from Ravi', false), note('n2', 'Old news', true)], unread: 1 },
  connected: true,
  markRead: vi.fn(async () => {}),
  markAllRead: vi.fn(async () => {}),
  version: () => 0,
  toasts: [],
  dismissToast: () => {},
};
vi.mock('../realtime/RealtimeContext', () => ({ useRealtime: () => realtime }));

const profile: Profile = {
  id: 'me', fullName: 'Asha Rao', email: 'asha@college.edu', bio: null, admin: false, skills: [],
  subjects: [{ id: 7, name: 'DBMS', proficiency: 'ADVANCED', verifiedLevel: null }],
};
const setProfile = vi.fn();
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ profile, setProfile }) }));

const breakdown = { knowledge: 1, reciprocity: 1, breadth: 0.33, proximity: 1 };
const suggestion = (id: string, name: string, score: number): Suggestion => ({ studentId: id, name, zone: 'Library', score, sharedSubjects: ['DBMS'], breakdown });
const match = (over: Partial<MatchView> = {}): MatchView => ({
  id: 'm1', status: 'PROPOSED', score: 88, breakdown, partner: { id: 'r', name: 'Ravi Patel', zone: 'Library', bio: null, email: null },
  sharedSubjects: ['DBMS'], youAccepted: true, partnerAccepted: false, createdAt: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 300_000).toISOString(), direct: true, requestedByYou: true, ...over,
});
const presenceOf = (id: string, name: string, zoneId: number, zone: string): PresenceView => ({
  studentId: id, name, zoneId, zone, status: 'AVAILABLE', requirements: null, seeking: [], subjects: [], expiresAt: new Date(Date.now() + 600_000).toISOString(),
});

function Where() {
  const l = useLocation();
  return <p data-testid="where">{l.pathname}</p>;
}

describe('NotificationBell', () => {
  it('lists only unread notifications, marks one read and links to the full page', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: /Notifications, 1 unread/ }));
    const dialog = screen.getByRole('dialog', { name: 'Unread notifications' });
    expect(within(dialog).getByText('Study request from Ravi')).toBeInTheDocument();
    expect(within(dialog).queryByText('Old news')).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Mark "Study request from Ravi" as read' }));
    expect(realtime.markRead).toHaveBeenCalledWith('n1');
    expect(within(dialog).getByRole('link', { name: 'View all notifications' })).toHaveAttribute('href', '/notifications');
  });
});

describe('NotificationsPage', () => {
  it('shows every notification and filters to unread ones', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>,
    );
    expect(screen.getByText('Old news')).toBeInTheDocument();
    expect(screen.getByText('Study request from Ravi')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Unread (1)' }));
    expect(screen.queryByText('Old news')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Mark read' }));
    expect(realtime.markRead).toHaveBeenCalledWith('n1');
  });
});

describe('Connect', () => {
  it('explains when a request cannot be sent', () => {
    expect(connectBlocker(undefined, false)).toMatch(/Check in/);
    expect(connectBlocker(match(), true)).toBe('Request pending with Ravi Patel');
    expect(connectBlocker(match({ status: 'ACCEPTED' }), true)).toBe('You are matched with Ravi Patel');
    expect(connectBlocker(undefined, true)).toBeNull();
  });

  it('sends a request and shows it was sent', async () => {
    const { calls } = mockApi({ 'POST /matching/requests': { body: match() } });
    const onSent = vi.fn();
    const user = userEvent.setup();
    render(<ConnectButton suggestion={suggestion('r', 'Ravi Patel', 91)} blocker={null} onSent={onSent} />);
    await user.click(screen.getByRole('button', { name: 'Connect with Ravi Patel' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Request sent');
    expect(calls.find((c) => c.key === 'POST /matching/requests')?.body).toEqual({ studentId: 'r' });
    expect(onSent).toHaveBeenCalled();
  });

  it('is disabled with the reason when blocked', () => {
    render(<ConnectButton suggestion={suggestion('r', 'Ravi Patel', 91)} blocker="Check in on campus to send requests" onSent={() => {}} />);
    const button = screen.getByRole('button', { name: 'Connect with Ravi Patel' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('title', 'Check in on campus to send requests');
  });
});

describe('Overview recommendations', () => {
  it('opens every recommendation in a dialog that closes again without leaving the page', async () => {
    const many = [...Array(8)].map((_, i) => suggestion(`s${i}`, `Student ${i}`, 90 - i));
    mockApi({
      'GET /presence/me': { body: presenceOf('me', 'Asha Rao', 1, 'Library') },
      'GET /presence/zones': { body: [{ id: 1, name: 'Library' }] },
      'GET /presence/available': { body: [] },
      'GET /matching/matches/current': { status: 204 },
      'GET /hackathons': { body: [] },
      'GET /matching/suggestions': { body: many },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    );
    expect(await screen.findAllByRole('button', { name: /^Connect with/ })).toHaveLength(6);
    await user.click(screen.getByRole('button', { name: /View all/ }));
    const dialog = screen.getByRole('dialog', { name: 'Recommended for you' });
    expect(within(dialog).getAllByRole('button', { name: /^Connect with/ })).toHaveLength(8);
    await user.click(within(dialog).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('ChatPanel', () => {
  it('shows the conversation and sends typed and quick-reply messages', async () => {
    const { calls } = mockApi({
      'GET /matching/matches/m1/messages': {
        body: [{ id: 'x1', senderId: 'r', mine: false, body: 'Hi! Library?', createdAt: new Date().toISOString() }],
      },
      'POST /matching/matches/m1/messages': (body) => ({
        body: { id: `x${Math.random()}`, senderId: 'me', mine: true, body: (body as { body: string }).body, createdAt: new Date().toISOString() },
      }),
    });
    const user = userEvent.setup();
    render(<ChatPanel match={match({ status: 'ACCEPTED', requestedByYou: false })} />);
    expect(await screen.findByText('Hi! Library?')).toBeInTheDocument();
    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'Coming in 5{Enter}');
    expect(await screen.findByText('Coming in 5')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Meet at Library?' }));
    expect(await screen.findByText('Meet at Library?', { selector: '.bubble span' })).toBeInTheDocument();
    expect(calls.filter((c) => c.key === 'POST /matching/matches/m1/messages').map((c) => c.body)).toEqual([
      { body: 'Coming in 5' }, { body: 'Meet at Library?' },
    ]);
  });
});

describe('CampusPage', () => {
  it('shows where you are checked in, highlights your zone and opens the find-a-peer form', async () => {
    const { calls } = mockApi({
      'GET /presence/zones': { body: [{ id: 1, name: 'Library' }, { id: 2, name: 'Cafeteria' }] },
      'GET /presence/me': { body: { ...presenceOf('me', 'Asha Rao', 1, 'Library'), seeking: ['DBMS'] } },
      'GET /presence/available': { body: [presenceOf('a', 'Ravi Patel', 1, 'Library'), presenceOf('b', 'Meera Shah', 2, 'Cafeteria')] },
      'GET /matching/matches/current': { status: 204 },
      'GET /matching/suggestions': { body: [suggestion('a', 'Ravi Patel', 91)] },
      'POST /presence/check-in': { body: presenceOf('me', 'Asha Rao', 2, 'Cafeteria') },
      'POST /matching/find': { body: match({ direct: false, requestedByYou: false }) },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/campus']}>
        <Routes>
          <Route path="/campus" element={<CampusPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('You are checked in')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Library: 1 peers, you are here/ })).toHaveClass('mine');
    expect(screen.getByRole('button', { name: /Check out/ })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Connect with Ravi Patel' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: /Find a peer/ }));
    const dialog = screen.getByRole('dialog', { name: 'Find a peer' });
    await user.selectOptions(within(dialog).getByLabelText('Where are you?'), '2');
    await user.click(within(dialog).getByRole('button', { name: /Update & find a peer/ }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/matches'));
    expect(calls.find((c) => c.key === 'POST /presence/check-in')?.body).toMatchObject({ zoneId: 2, status: 'AVAILABLE', seekingSubjectIds: [7] });
    expect(calls.some((c) => c.key === 'POST /matching/find')).toBe(true);
  });
});

describe('Find a peer form', () => {
  it('adds a new subject with a level, saves it to the profile and ticks it', async () => {
    const { calls } = mockApi({
      'GET /presence/zones': { body: [{ id: 1, name: 'Library' }] },
      'GET /presence/me': { status: 204 },
      'GET /presence/available': { body: [] },
      'GET /matching/matches/current': { status: 204 },
      'POST /students/me/subjects': {
        body: { ...profile, subjects: [...profile.subjects, { id: 9, name: 'Operating Systems', proficiency: 'BEGINNER', verifiedLevel: null }] },
      },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <CampusPage />
      </MemoryRouter>,
    );
    await user.click(await screen.findByRole('button', { name: /Find a peer/ }));
    const dialog = screen.getByRole('dialog', { name: 'Find a peer' });
    await user.type(within(dialog).getByRole('textbox', { name: 'New subject' }), 'Operating Systems');
    await user.selectOptions(within(dialog).getByLabelText('Your level in the new subject'), 'BEGINNER');
    await user.click(within(dialog).getByRole('button', { name: 'Add' }));
    await waitFor(() => expect(setProfile).toHaveBeenCalled());
    expect(calls.find((c) => c.key === 'POST /students/me/subjects')?.body).toEqual({ name: 'Operating Systems', proficiency: 'BEGINNER' });
    expect(within(dialog).getByRole('textbox', { name: 'New subject' })).toHaveValue('');
  });
});
